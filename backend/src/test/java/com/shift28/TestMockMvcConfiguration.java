package com.shift28;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestMockMvcConfiguration {

	@Bean
	MockMvcBuilderCustomizer publishRequestHandledEvents(WebMvcProperties properties) {
		return builder -> builder.addDispatcherServletCustomizer(
				servlet -> servlet.setPublishEvents(properties.isPublishRequestHandledEvents()));
	}

}
