package com.shift28.domain;

import static com.shift28.domain.Fixtures.WINTER;
import static com.shift28.domain.Fixtures.assertInvalid;
import static com.shift28.domain.Fixtures.date;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VacationPeriodTest {

	@Test
	void coversItsDatesInclusively() {
		assertThat(WINTER.covers(date("2026-12-20"))).isFalse();
		assertThat(WINTER.covers(date("2026-12-21"))).isTrue();
		assertThat(WINTER.covers(date("2027-01-06"))).isTrue();
		assertThat(WINTER.covers(date("2027-01-07"))).isFalse();
	}

	@Test
	void singleDayPeriodIsAllowed() {
		assertThat(new VacationPeriod(date("2026-12-24"), date("2026-12-24")).covers(date("2026-12-24"))).isTrue();
	}

	@Test
	void endIsNotBeforeStart() {
		assertInvalid()
			.isThrownBy(() -> new VacationPeriod(date("2026-12-24"), date("2026-12-23")));
	}

}
