package guru.sfg.beer.order.service.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.DateTimeFeature;

@Configuration
public class JacksonConfig {
//	@Bean
//	@Primary
//	ObjectMapper objectMapper() {
//		return new ObjectMapper().registerModule(new JavaTimeModule());
//	}

	@Bean
	JsonMapperBuilderCustomizer jacksonCustomizer() {
		return builder -> {
			builder.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, //
					DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
		};
	}
}
