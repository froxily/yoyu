package com.shift28.web;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

record DateRangeParams(@NotNull LocalDate from, @NotNull LocalDate to) {

	private static final int MAX_DAYS = 366;

	@AssertTrue(message = "from must not be after to")
	boolean isOrdered() {
		return from == null || to == null || !from.isAfter(to);
	}

	@AssertTrue(message = "the range must cover at most 366 days")
	boolean isWithinLimit() {
		return from == null || to == null || ChronoUnit.DAYS.between(from, to) < MAX_DAYS;
	}

}
