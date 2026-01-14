package guru.sfg.beer.order.service.sm;

import org.springframework.stereotype.Component;

import com.github.oxo42.stateless4j.StateMachine;
import com.github.oxo42.stateless4j.StateMachineConfig;

import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;

@Component
public class BeerOrderStateMachineConfig {

	public BeerOrderStateMachine create() {

		BeerOrderStatusEnum initialState = BeerOrderStatusEnum.NEW;

		StateMachineConfig<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachineConfig = new StateMachineConfig<>();

		stateMachineConfig.configure(BeerOrderStatusEnum.NEW) //
			.ignore(BeerOrderEventEnum.VALIDATE_ORDER) //
			.permit(BeerOrderEventEnum.VALIDATION_PASSED, BeerOrderStatusEnum.VALIDATED) //
			.permit(BeerOrderEventEnum.VALIDATION_FAILED, BeerOrderStatusEnum.VALIDATION_EXCEPTION);

		// Stati terminali
		stateMachineConfig.configure(BeerOrderStatusEnum.PICKED_UP); //
		stateMachineConfig.configure(BeerOrderStatusEnum.DELIVERED); //
		stateMachineConfig.configure(BeerOrderStatusEnum.DELIVERY_EXCEPTION); //
		stateMachineConfig.configure(BeerOrderStatusEnum.VALIDATION_EXCEPTION); //
		stateMachineConfig.configure(BeerOrderStatusEnum.ALLOCATION_EXCEPTION); //

		return new BeerOrderStateMachine(
				new StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum>(initialState, stateMachineConfig));
	}
}
