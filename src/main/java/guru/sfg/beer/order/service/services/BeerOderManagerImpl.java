package guru.sfg.beer.order.service.services;

import org.springframework.stereotype.Service;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.BeerOrderStateMachine;
import guru.sfg.beer.order.service.sm.BeerOrderStateMachineFactory;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

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
}
