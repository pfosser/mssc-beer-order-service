package guru.sfg.beer.order.service;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderLine;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.beer.order.service.services.beer.BeerServiceImpl;
import guru.sfg.brewery.model.BeerDto;
import guru.sfg.brewery.model.BeerPagedList;

@SpringBootTest
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
	void testNewAllocated() throws JsonProcessingException {
		BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
		List<BeerDto> list = List.of(beerDto);
		// BeerPagedList beerPagedList = new BeerPagedList(list, PageRequest.of(0, list.size()), list.size());

		wireMockServer.stubFor(get(BeerServiceImpl.BEER_UPC_PATH_V1 + "12345") //
				.willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

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
				.upc("12345") //
				.orderQuantity(1) //
				.beerOrder(beerOrder) //
				.build());

		beerOrder.setBeerOrderLines(lines);

		return beerOrder;
	}
}
