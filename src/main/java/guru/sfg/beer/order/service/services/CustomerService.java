package guru.sfg.beer.order.service.services;

import org.springframework.data.domain.Pageable;

import guru.sfg.brewery.model.CustomerPagedList;

public interface CustomerService {

	CustomerPagedList listCustomers(Pageable pageable);

}
