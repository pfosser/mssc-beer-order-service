package guru.sfg.beer.order.service.services.listeners;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.brewery.model.events.AllocateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationResultListener {

	private BeerOrderManager beerOrderManager;

	@JmsListener(destination = JmsConfig.ALLOCATE_ORDER_RESPONSE_QUEUE)
	public void listen(AllocateOrderResult result) {
		if (!result.getAllocationError() && !result.getPendingInventory()) {
			// allocated normally
			beerOrderManager.beerOrderAllocationPassed(result.getBeerOrder());
		} else if (!result.getAllocationError() && result.getPendingInventory()) {
			// pending inventory
			beerOrderManager.beerOrderAllocationPendingInventory(result.getBeerOrder());
		} else if (result.getAllocationError()) {
			// allocation error
			beerOrderManager.beerOrderAllocationFailed(result.getBeerOrder());
		}
	}
}
