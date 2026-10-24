package com.shift28.domain;

import java.time.LocalDate;

public record VacationPeriod(LocalDate start, LocalDate end) {

	public VacationPeriod {
		if (start == null || end == null || end.isBefore(start)) {
			throw new DomainValidationException("a vacation period must not end before it starts");
		}
	}

	public boolean covers(LocalDate date) {
		return !date.isBefore(start) && !date.isAfter(end);
	}

}
