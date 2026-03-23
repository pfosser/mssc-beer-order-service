package guru.sfg.beer.order.service.testcomponents;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationListener {

	private final JmsTemplate jmsTemplate;

	@JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
	public void listen(Message<AllocateOrderRequest> msg) {
		AllocateOrderRequest request = msg.getPayload();
		boolean pendingInventory = false;
		boolean allocationError = false;
		boolean sendResponse = true;

		// set allocation error
		String customerRef = request.getBeerOrder().getCustomerRef();
		if ("fail-allocation".equals(customerRef)) {
			allocationError = true;
		} else if ("partial-allocation".equals(customerRef)) {
			pendingInventory = true;
		} else if ("dont-allocate".equals(customerRef)) {
			sendResponse = false;
		}

		boolean finalPendingInventory = pendingInventory;

		request.getBeerOrder().getBeerOrderLines().forEach(beerOrderLineDto -> {
			if (finalPendingInventory) {
				beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity() - 1);
			} else {
				beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity());
			}
		});

		if (sendResponse) {
			jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESPONSE_QUEUE, AllocateOrderResult.builder() //
					.beerOrder(request.getBeerOrder()) //
					.pendingInventory(pendingInventory) //
					.allocationError(allocationError) //
					.build());
		}
	}
}