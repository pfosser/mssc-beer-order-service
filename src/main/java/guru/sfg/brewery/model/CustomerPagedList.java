package guru.sfg.brewery.model;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class CustomerPagedList extends PageImpl<CustomerDto> {
	private static final long serialVersionUID = 2811248331901595683L;

	public CustomerPagedList(List<CustomerDto> content, Pageable pageable, long total) {
		super(content, pageable, total);
	}

	public CustomerPagedList(List<CustomerDto> content) {
		super(content);
	}
}
