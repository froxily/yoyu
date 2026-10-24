package com.shift28.domain;

import java.time.LocalDate;

public record RuleSet(LocalDate effectiveFrom, int regularWindowLimit, int vacationWindowLimit, int vacationDayLimit) {

	public static final RuleSet BASELINE = new RuleSet(LocalDate.of(2000, 1, 1), Rules.REGULAR_WINDOW_LIMIT,
			Rules.VACATION_WINDOW_LIMIT, Rules.VACATION_DAY_LIMIT);

	public RuleSet {
		if (effectiveFrom == null) {
			throw new IllegalArgumentException("a rule version needs an effective date");
		}
		if (regularWindowLimit <= 0 || vacationWindowLimit <= 0 || vacationDayLimit <= 0) {
			throw new IllegalArgumentException("rule limits must be positive");
		}
	}

}
