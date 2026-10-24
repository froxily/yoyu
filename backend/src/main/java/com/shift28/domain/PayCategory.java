package com.shift28.domain;

import java.math.BigDecimal;

public enum PayCategory {

	NORMAL("1.00"), LATE_NIGHT("1.25"), OVERTIME("1.25"), OVERTIME_LATE_NIGHT("1.50");

	private final BigDecimal multiplier;

	PayCategory(String multiplier) {
		this.multiplier = new BigDecimal(multiplier);
	}

	public BigDecimal multiplier() {
		return multiplier;
	}

	public static PayCategory of(boolean overtime, boolean lateNight) {
		if (overtime) {
			return lateNight ? OVERTIME_LATE_NIGHT : OVERTIME;
		}
		return lateNight ? LATE_NIGHT : NORMAL;
	}

}
