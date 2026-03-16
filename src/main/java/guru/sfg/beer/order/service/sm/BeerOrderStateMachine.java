package guru.sfg.beer.order.service.sm;

import com.github.oxo42.stateless4j.StateMachine;

import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class BeerOrderStateMachine {

	private final StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> machine;
	
	public BeerOrderStatusEnum getState() {
		return machine.getState();
	}

	public void sendEvent(BeerOrderEventEnum event) {
		log.trace("Firing {} event", event);
		machine.fire(event);
	}
}
