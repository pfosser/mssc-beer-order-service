package guru.sfg.beer.order.service.sm;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import com.github.oxo42.stateless4j.StateConfiguration;
import com.github.oxo42.stateless4j.StateMachine;
import com.github.oxo42.stateless4j.StateMachineConfig;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.web.mappers.BeerOrderMapper;
import guru.sfg.brewery.model.BeerOrderDto;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocationFailureEvent;
import guru.sfg.brewery.model.events.DeallocateOrderRequest;
import guru.sfg.brewery.model.events.ValidateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderStateMachineFactory {

	private final BeerOrderRepository beerOrderRepository;
	private final BeerOrderMapper beerOrderMapper;
	private final JmsTemplate jmsTemplate;

	public BeerOrderStateMachine create() {
		return createInternal(null, BeerOrderStatusEnum.NEW);
	}

	public BeerOrderStateMachine getStateMachine(UUID id, BeerOrderStatusEnum initialState) {
		return createInternal(id, initialState);
	}

	private BeerOrderStateMachine createInternal(UUID id, BeerOrderStatusEnum initialState) {

		StateMachineConfig<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachineConfig = new StateMachineConfig<>();

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.NEW) //
					.permit(BeerOrderEventEnum.VALIDATE_ORDER, BeerOrderStatusEnum.VALIDATION_PENDING);
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.VALIDATION_PENDING) //
					.permit(BeerOrderEventEnum.VALIDATION_PASSED, BeerOrderStatusEnum.VALIDATED) //
					.permit(BeerOrderEventEnum.VALIDATION_FAILED, BeerOrderStatusEnum.VALIDATION_EXCEPTION) //
					.permit(BeerOrderEventEnum.CANCEL_ORDER, BeerOrderStatusEnum.CANCELED);
			addPersistence(id, statusConfig);
			statusConfig.onEntry(() -> {
				BeerOrder beerOrder = beerOrderRepository.getReferenceById(id);
				BeerOrderDto beerOrderDto = beerOrderMapper.beerOrderToDto(beerOrder);

				log.debug("Send validation request to queue for order id {}", id);

				jmsTemplate.convertAndSend(JmsConfig.VALIDATE_ORDER_QUEUE, ValidateOrderRequest.builder() //
						.beerOrder(beerOrderDto) //
						.build());
			}); //

		}
		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.VALIDATED) //
					.permit(BeerOrderEventEnum.ALLOCATE_ORDER, BeerOrderStatusEnum.ALLOCATION_PENDING) //
					.permit(BeerOrderEventEnum.CANCEL_ORDER, BeerOrderStatusEnum.CANCELED);
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.ALLOCATION_PENDING) //
					.permit(BeerOrderEventEnum.ALLOCATION_SUCCESS, BeerOrderStatusEnum.ALLOCATED) //
					.permit(BeerOrderEventEnum.ALLOCATION_FAILED, BeerOrderStatusEnum.ALLOCATION_EXCEPTION) //
					.permit(BeerOrderEventEnum.ALLOCATION_NO_INVENTORY, BeerOrderStatusEnum.PENDING_INVENTORY) //
					.permit(BeerOrderEventEnum.CANCEL_ORDER, BeerOrderStatusEnum.CANCELED);
			statusConfig.onEntry(() -> {
				BeerOrder beerOrder = beerOrderRepository.getReferenceById(id);
				BeerOrderDto beerOrderDto = beerOrderMapper.beerOrderToDto(beerOrder);

				log.debug("Send allocation request to queue for order id {}", id);

				jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_QUEUE, AllocateOrderRequest.builder() //
						.beerOrder(beerOrderDto) //
						.build());
			}); //
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.ALLOCATED) //
					.permit(BeerOrderEventEnum.BEER_ORDER_PICKED_UP, BeerOrderStatusEnum.PICKED_UP) //
					.permit(BeerOrderEventEnum.CANCEL_ORDER, BeerOrderStatusEnum.CANCELED);
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.PENDING_INVENTORY); //
			addPersistence(id, statusConfig);
		}

		// Stati terminali
		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.PICKED_UP); //
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.DELIVERED); //
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.DELIVERY_EXCEPTION); //
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.VALIDATION_EXCEPTION); //
			statusConfig.onEntry(() -> {
				log.error("Compensating transaction... Validation failed: {}", id);
			});
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.ALLOCATION_EXCEPTION); //
			statusConfig.onEntry(() -> {
				jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_FAILURE_QUEUE, AllocationFailureEvent.builder() //
						.orderId(id).build());
				log.debug("Sent allocation failure message to queue for order id {}", id);
			});
			addPersistence(id, statusConfig);
		}

		{
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig = stateMachineConfig
					.configure(BeerOrderStatusEnum.CANCELED); //
			statusConfig.onEntry((transition) -> {
				if (transition.getSource() == BeerOrderStatusEnum.ALLOCATED) {
					Optional<BeerOrder> beerOrderOpt = beerOrderRepository.findById(id);
					beerOrderOpt.ifPresentOrElse((beerOrder) -> {

						BeerOrderDto beerOrderDto = beerOrderMapper.beerOrderToDto(beerOrder);

						log.debug("Send deallocation request to queue for order id {}", id);

						jmsTemplate.convertAndSend(JmsConfig.DEALLOCATE_ORDER_QUEUE, DeallocateOrderRequest.builder() //
								.beerOrder(beerOrderDto) //
								.build());
					}, () -> log.error("Beer order with id {} not found", id));
				}
			});
			addPersistence(id, statusConfig);
		}

		return new BeerOrderStateMachine(
				new StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum>(initialState, stateMachineConfig));
	}

	private StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> addPersistence(UUID id,
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> config) {
		return config.onEntry((transition) -> {

			log.debug("Saving beer order with id {} and state {}", id, transition.getDestination());

			Optional<BeerOrder> beerOrderOpt = beerOrderRepository.findById(id);
			beerOrderOpt.ifPresentOrElse(beerOrder -> {
				beerOrder.setOrderStatus(transition.getDestination());
				beerOrderRepository.saveAndFlush(beerOrder);
			}, () -> log.error("Beer order not found for saving"));
		}); //
	}

}
