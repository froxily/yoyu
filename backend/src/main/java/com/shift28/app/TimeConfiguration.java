package com.shift28.app;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class TimeConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
