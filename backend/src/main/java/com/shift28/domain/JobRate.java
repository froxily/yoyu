package com.shift28.domain;

public record JobRate(long hourlyWageYen, long transportYen) {

	public JobRate {
		if (hourlyWageYen <= 0) {
			throw new DomainValidationException("the hourly wage must be positive");
		}
		if (transportYen < 0) {
			throw new DomainValidationException("the transport allowance must not be negative");
		}
	}

}
