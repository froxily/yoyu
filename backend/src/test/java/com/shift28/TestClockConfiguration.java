package com.shift28;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	public static final Instant DEFAULT_NOW = OffsetDateTime.parse("2027-06-01T09:00:00+09:00").toInstant();

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(DEFAULT_NOW);
	}

}
