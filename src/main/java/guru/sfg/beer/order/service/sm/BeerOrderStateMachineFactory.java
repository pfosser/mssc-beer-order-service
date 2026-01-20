package guru.sfg.beer.order.service.sm;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.oxo42.stateless4j.StateConfiguration;
import com.github.oxo42.stateless4j.StateMachine;
import com.github.oxo42.stateless4j.StateMachineConfig;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderStateMachineFactory {

	private final BeerOrderRepository beerOrderRepository;

	public BeerOrderStateMachine create() {
		return createInternal(null, BeerOrderStatusEnum.NEW);
	}

	public BeerOrderStateMachine getStateMachine(UUID id, BeerOrderStatusEnum initialState) {
		return createInternal(id, initialState);
	}

	private BeerOrderStateMachine createInternal(UUID id, BeerOrderStatusEnum initialState) {

		StateMachineConfig<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachineConfig = new StateMachineConfig<>();

		StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> statusConfig;

		statusConfig = stateMachineConfig.configure(BeerOrderStatusEnum.NEW) //
				.ignore(BeerOrderEventEnum.VALIDATE_ORDER) //
				.permit(BeerOrderEventEnum.VALIDATION_PASSED, BeerOrderStatusEnum.VALIDATED) //
				.permit(BeerOrderEventEnum.VALIDATION_FAILED, BeerOrderStatusEnum.VALIDATION_EXCEPTION);
		addPersistence(id, statusConfig);

		// Stati terminali
		statusConfig = stateMachineConfig.configure(BeerOrderStatusEnum.PICKED_UP); //
		addPersistence(id, statusConfig);

		statusConfig = stateMachineConfig.configure(BeerOrderStatusEnum.DELIVERED); //
		addPersistence(id, statusConfig);

		statusConfig = stateMachineConfig.configure(BeerOrderStatusEnum.DELIVERY_EXCEPTION); //
		addPersistence(id, statusConfig);

		statusConfig = stateMachineConfig.configure(BeerOrderStatusEnum.VALIDATION_EXCEPTION); //
		addPersistence(id, statusConfig);

		statusConfig = stateMachineConfig.configure(BeerOrderStatusEnum.ALLOCATION_EXCEPTION); //
		addPersistence(id, statusConfig);

		return new BeerOrderStateMachine(
				new StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum>(initialState, stateMachineConfig));
	}

	private StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> addPersistence(UUID id,
			StateConfiguration<BeerOrderStatusEnum, BeerOrderEventEnum> config) {
		return config.onEntry((transition) -> {

			log.debug("Saving beer order with id {} and state {}", id, transition.getDestination());

			BeerOrder beerOrder = beerOrderRepository.getReferenceById(id);
			beerOrder.setOrderStatus(transition.getDestination());
			beerOrderRepository.saveAndFlush(beerOrder);
		}); //
	}

}
