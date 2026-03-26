package guru.sfg.beer.order.service;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jms.core.JmsTemplate;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderLine;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.beer.order.service.services.beer.BeerServiceImpl;
import guru.sfg.brewery.model.BeerDto;
import guru.sfg.brewery.model.events.AllocationFailureEvent;
import guru.sfg.brewery.model.events.DeallocateOrderRequest;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@EnableWireMock({ @ConfigureWireMock(name = "wiremock", port = 8083) })
public class BeerOrderManagerImplIT {

	@Autowired
	BeerOrderManager beerOrderManager;

	@Autowired
	BeerOrderRepository beerOrderRepository;

	@Autowired
	CustomerRepository customerRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JmsTemplate jmsTemplate;

	Customer testCustomer;

	UUID beerId = UUID.randomUUID();

//	@TestConfiguration
//	static class RestTemplateBuilderProvider {
//		@Bean(destroyMethod = "stop")
//		public WireMockServer wireMockServer() {
//			WireMockServer server = with(wireMockConfig().port(8083));
//			server.start();
//			return server;
//		}
//	}

	@InjectWireMock
	WireMockServer wireMockServer;

	@BeforeEach
	void setUp() {
		testCustomer = customerRepository.save(Customer.builder() //
				.customerName("Test Customer") //
				.build());
	}

	@Test
	void testNewToAllocated() throws JsonProcessingException, InterruptedException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.ALLOCATED == foundOrder.getOrderStatus());
		});

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
			BeerOrderLine line = foundOrder.getBeerOrderLines().iterator().next();
			Assertions.assertThat(line.getOrderQuantity().equals(line.getQuantityAllocated()));
		});

		BeerOrder savedBeerOrder2 = beerOrderRepository.findById(savedBeerOrder.getId()).get();

		assertNotNull(savedBeerOrder2);
		assertEquals(BeerOrderStatusEnum.ALLOCATED, savedBeerOrder2.getOrderStatus());
		savedBeerOrder2.getBeerOrderLines().forEach(line -> {
			assertEquals(line.getOrderQuantity(), line.getQuantityAllocated());
		});
	}

	@Test
	public void testFailedValidation() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("fail-validation");

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.VALIDATION_EXCEPTION == foundOrder.getOrderStatus());
		});

	}

	@Test
	public void testFailedAllocation() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("fail-allocation");

		// Questo è necessario: ha dei side-effect
		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.ALLOCATION_EXCEPTION == foundOrder.getOrderStatus());
		});

		AllocationFailureEvent allocationFailureEvent = (AllocationFailureEvent) jmsTemplate
				.receiveAndConvert(JmsConfig.ALLOCATE_FAILURE_QUEUE);

		assertNotNull(allocationFailureEvent);
		assertThat(allocationFailureEvent.getOrderId()).isEqualTo(savedBeerOrder.getId());
	}

	@Test
	public void testPartialAllocation() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("partial-allocation");

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.PENDING_INVENTORY == foundOrder.getOrderStatus());
		});

	}

	@Test
	public void testNewToPickedUp() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.ALLOCATED == foundOrder.getOrderStatus());
		});

		beerOrderManager.beerOrderPickedUp(savedBeerOrder.getId());

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.PICKED_UP == foundOrder.getOrderStatus());
		});

		BeerOrder pickedUp = beerOrderRepository.findById(savedBeerOrder.getId()).get();

		assertEquals(BeerOrderStatusEnum.PICKED_UP, pickedUp.getOrderStatus());
	}

	@Test
	public void testValidationPendingToCancel() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("dont-validate");

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.VALIDATION_PENDING == foundOrder.getOrderStatus());
		});

		beerOrderManager.cancelOrder(savedBeerOrder.getId());

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.CANCELED == foundOrder.getOrderStatus());
		});
	}

	@Test
	public void testAllocationPendingToCancel() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();
		beerOrder.setCustomerRef("dont-allocate");

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.ALLOCATION_PENDING == foundOrder.getOrderStatus());
		});

		beerOrderManager.cancelOrder(savedBeerOrder.getId());

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.CANCELED == foundOrder.getOrderStatus());
		});
	}

	@Test
	public void testAllocatedToCancel() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

		BeerOrder beerOrder = createBeerOrder();

		BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.ALLOCATED == foundOrder.getOrderStatus());
		});

		beerOrderManager.cancelOrder(savedBeerOrder.getId());

		await().untilAsserted(() -> {
			BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

			Assertions.assertThat(BeerOrderStatusEnum.CANCELED == foundOrder.getOrderStatus());
		});

		DeallocateOrderRequest deallocateOrderRequest = (DeallocateOrderRequest) jmsTemplate
				.receiveAndConvert(JmsConfig.DEALLOCATE_ORDER_QUEUE);

		assertNotNull(deallocateOrderRequest);
		assertThat(deallocateOrderRequest.getBeerOrder().getId()).isEqualTo(savedBeerOrder.getId());
	}

	public BeerOrder createBeerOrder() {
		BeerOrder beerOrder = BeerOrder.builder() //
				.customer(testCustomer) //
				.build();

		Set<BeerOrderLine> lines = new HashSet<>();
		lines.add(BeerOrderLine.builder() //
				.beerId(beerId) //
				.upc("12345") //
				.orderQuantity(1) //
				.beerOrder(beerOrder) //
				.build());

		beerOrder.setBeerOrderLines(lines);

		return beerOrder;
	}
}
