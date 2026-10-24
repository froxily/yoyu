package com.shift28.domain;

import static com.shift28.domain.Attribution.CALENDAR_SPLIT;
import static com.shift28.domain.Attribution.START_DAY;
import static com.shift28.domain.Fixtures.JOB_A;
import static com.shift28.domain.Fixtures.date;
import static com.shift28.domain.Fixtures.jst;
import static com.shift28.domain.Fixtures.shift;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttributionTest {

	private static final TimeZone ORIGINAL_DEFAULT_ZONE = TimeZone.getDefault();

	@AfterEach
	void restoreDefaultZone() {
		TimeZone.setDefault(ORIGINAL_DEFAULT_ZONE);
	}

	@Test
	@DisplayName("T5: Nov 7 22:00 → Nov 8 03:00 splits 120/180 by calendar and puts 300 on Nov 7 by start day")
	void t5MidnightCrossingShift() {
		Shift shift = shift("2026-11-07T22:00", "2026-11-08T03:00");

		assertThat(CALENDAR_SPLIT.minutesByDate(shift))
			.containsOnly(entry(date("2026-11-07"), 120), entry(date("2026-11-08"), 180));
		assertThat(START_DAY.minutesByDate(shift)).containsOnly(entry(date("2026-11-07"), 300));
	}

	@Test
	@DisplayName("T7: Nov 7 20:00 → Nov 8 04:00 with break 23:30–00:30 splits 210/210 and puts 420 on Nov 7")
	void t7BreakAcrossMidnight() {
		Shift shift = shift("2026-11-07T20:00", "2026-11-08T04:00", "2026-11-07T23:30", "2026-11-08T00:30");

		assertThat(CALENDAR_SPLIT.minutesByDate(shift))
			.containsOnly(entry(date("2026-11-07"), 210), entry(date("2026-11-08"), 210));
		assertThat(START_DAY.minutesByDate(shift)).containsOnly(entry(date("2026-11-07"), 420));
	}

	@ParameterizedTest(name = "JVM default zone {0}")
	@ValueSource(strings = { "UTC", "Asia/Kolkata" })
	@DisplayName("T15: 19:30+05:30 → 21:30+05:30 is Nov 2 23:00 → Nov 3 01:00 JST whatever the JVM default zone")
	void t15ResultsDoNotDependOnTheJvmDefaultZone(String defaultZone) {
		TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));

		Shift shift = new Shift(UUID.randomUUID(), JOB_A, OffsetDateTime.parse("2026-11-02T19:30+05:30").toInstant(),
				OffsetDateTime.parse("2026-11-02T21:30+05:30").toInstant());

		assertThat(shift.startAt()).isEqualTo(jst("2026-11-02T23:00"));
		assertThat(shift.endAt()).isEqualTo(jst("2026-11-03T01:00"));
		assertThat(CALENDAR_SPLIT.minutesByDate(shift))
			.containsOnly(entry(date("2026-11-02"), 60), entry(date("2026-11-03"), 60));
		assertThat(START_DAY.minutesByDate(shift)).containsOnly(entry(date("2026-11-02"), 120));

		Check window = new ComplianceEngine(List.of(shift), List.of())
			.evaluate(date("2026-11-02"), date("2026-11-03"))
			.window(date("2026-11-03"))
			.orElseThrow();
		assertThat(window.under(CALENDAR_SPLIT).minutes()).isEqualTo(60);
		assertThat(window.under(START_DAY).minutes()).isZero();
	}

}
