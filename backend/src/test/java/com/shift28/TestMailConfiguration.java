package com.shift28;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {

	@Bean
	@Primary
	CapturingMailService capturingMailService() {
		return new CapturingMailService();
	}

}
