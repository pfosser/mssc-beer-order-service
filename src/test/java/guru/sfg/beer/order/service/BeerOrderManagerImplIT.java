package guru.sfg.beer.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderLine;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.beer.order.service.services.BeerOrderManager;

@SpringBootTest
public class BeerOrderManagerImplIT {

	@Autowired
	BeerOrderManager beerOrderManager;

	@Autowired
	BeerOrderRepository beerOrderRepository;

	@Autowired
	CustomerRepository customerRepository;
	
	Customer testCustomer;
	
	UUID beerId = UUID.randomUUID();
	
	@BeforeEach
	void setUp() {
		testCustomer = customerRepository.save(Customer.builder() //
				.customerName("Test Customer") //
				.build());
	}
	
	@Test
	void testNewAllocated() {
		BeerOrder beerOrder = createBeerOrder();
		
		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);
		
		assertNotNull(savedBeerOrder);
		assertEquals(BeerOrderStatusEnum.ALLOCATED, savedBeerOrder.getOrderStatus());
	}
	
	public BeerOrder createBeerOrder() {
		BeerOrder beerOrder = BeerOrder.builder() //
				.customer(testCustomer) //
				.build();
		
		Set<BeerOrderLine> lines = new HashSet<>();
		lines.add(BeerOrderLine.builder() //
				.beerId(beerId) //
				.upc("0631234200036") //
				.orderQuantity(1) //
				.beerOrder(beerOrder) //
				.build());
		
		beerOrder.setBeerOrderLines(lines);
		
		return beerOrder;
	}
}
