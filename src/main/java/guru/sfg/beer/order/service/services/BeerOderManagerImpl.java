package guru.sfg.beer.order.service.services;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.BeerOrderStateMachine;
import guru.sfg.beer.order.service.sm.BeerOrderStateMachineFactory;
import guru.sfg.brewery.model.BeerOrderDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class BeerOderManagerImpl implements BeerOrderManager {

	private final BeerOrderStateMachineFactory stateMachineConfig;

	private final BeerOrderRepository beerOrderRepository;

	@Transactional
	@Override
	public BeerOrder newBeerOrder(BeerOrder beerOrder) {
		beerOrder.setId(null);
		beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);

		BeerOrder savedBeerOrder = beerOrderRepository.save(beerOrder);
		sendBeeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.VALIDATE_ORDER);

		return savedBeerOrder;
	}

	private void sendBeeerOrderEvent(BeerOrder beerOrder, BeerOrderEventEnum event) {
		BeerOrderStateMachine sm = build(beerOrder);

		sm.sendEvent(event);
	}

	private BeerOrderStateMachine build(BeerOrder beerOrder) {
		BeerOrderStateMachine sm = stateMachineConfig.getStateMachine(beerOrder.getId(), beerOrder.getOrderStatus());

		return sm;
	}

	@Override
	public void processValidationResult(UUID beerOrderId, Boolean isValid) {
		BeerOrder beerOrder = beerOrderRepository.getReferenceById(beerOrderId);

		BeerOrderStateMachine sm = build(beerOrder);

		if (isValid) {
			sm.sendEvent(BeerOrderEventEnum.VALIDATION_PASSED);

			sm.sendEvent(BeerOrderEventEnum.ALLOCATE_ORDER);
		} else {
			sm.sendEvent(BeerOrderEventEnum.VALIDATION_FAILED);

		}
	}

	@Override
	public void beerOrderAllocationPassed(BeerOrderDto beerOrderDto) {
		Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

		beerOrderOptional.ifPresentOrElse(beerOrder -> {
			BeerOrderStateMachine sm = build(beerOrder);
			sm.sendEvent(BeerOrderEventEnum.ALLOCATION_SUCCESS);
			awaitForStatus(beerOrder.getId(), BeerOrderStatusEnum.ALLOCATED);
			updateAllocatedQty(beerOrderDto);
		}, () -> log.error("Order Id Not Found: " + beerOrderDto.getId()));
	}

	@Override
	public void beerOrderAllocationPendingInventory(BeerOrderDto beerOrderDto) {
		Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

		beerOrderOptional.ifPresentOrElse(beerOrder -> {
			BeerOrderStateMachine sm = build(beerOrder);
			sm.sendEvent(BeerOrderEventEnum.ALLOCATION_NO_INVENTORY);
			awaitForStatus(beerOrder.getId(), BeerOrderStatusEnum.PENDING_INVENTORY);
			updateAllocatedQty(beerOrderDto);
		}, () -> log.error("Order Id Not Found: " + beerOrderDto.getId()));

	}

	private void updateAllocatedQty(BeerOrderDto beerOrderDto) {
		Optional<BeerOrder> allocatedOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

		allocatedOrderOptional.ifPresentOrElse(allocatedOrder -> {
			allocatedOrder.getBeerOrderLines().forEach(beerOrderLine -> {
				beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
					if (beerOrderLine.getId().equals(beerOrderLineDto.getId())) {
						beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
					}
				});
			});

			beerOrderRepository.saveAndFlush(allocatedOrder);
		}, () -> log.error("Order Not Found. Id: " + beerOrderDto.getId()));
	}

	@Override
	public void beerOrderAllocationFailed(BeerOrderDto beerOrderDto) {
		Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

		beerOrderOptional.ifPresentOrElse(beerOrder -> {
			BeerOrderStateMachine sm = build(beerOrder);
			sm.sendEvent(BeerOrderEventEnum.ALLOCATION_FAILED);
		}, () -> log.error("Order Not Found. Id: " + beerOrderDto.getId()));

	}

	private void awaitForStatus(UUID beerOrderId, BeerOrderStatusEnum statusEnum) {

		AtomicBoolean found = new AtomicBoolean(false);
		AtomicInteger loopCount = new AtomicInteger(0);

		while (!found.get()) {
			if (loopCount.incrementAndGet() > 10) {
				found.set(true);
				log.debug("Loop Retries exceeded");
			}

			beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
				if (beerOrder.getOrderStatus().equals(statusEnum)) {
					found.set(true);
					log.debug("Order Found");
				} else {
					log.debug("Order Status Not Equal. Expected: " + statusEnum.name() + " Found: "
							+ beerOrder.getOrderStatus().name());
				}
			}, () -> {
				log.debug("Order Id Not Found");
			});

			if (!found.get()) {
				try {
					log.debug("Sleeping for retry");
					Thread.sleep(100);
				} catch (Exception e) {
					// do nothing
				}
			}
		}
	}
}
