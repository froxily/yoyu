package com.shift28.domain;

import static com.shift28.domain.Fixtures.JOB_A;
import static com.shift28.domain.Fixtures.assertInvalid;
import static com.shift28.domain.Fixtures.date;
import static com.shift28.domain.Fixtures.jst;
import static com.shift28.domain.Fixtures.shift;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShiftTest {

	@Test
	@DisplayName("T6: Nov 2 10:00–18:00 with break 13:00–14:00 works 420 minutes")
	void t6BreakIsSubtractedFromWorkedTime() {
		Shift shift = shift("2026-11-02T10:00", "2026-11-02T18:00", "2026-11-02T13:00", "2026-11-02T14:00");

		assertThat(shift.workedMinutes()).isEqualTo(420);
	}

	@Test
	void workedIntervalsLeaveOutTheBreak() {
		Shift shift = shift("2026-11-02T10:00", "2026-11-02T18:00", "2026-11-02T13:00", "2026-11-02T14:00");

		assertThat(shift.workedIntervals()).containsExactly(
				new Interval(jst("2026-11-02T10:00"), jst("2026-11-02T13:00")),
				new Interval(jst("2026-11-02T14:00"), jst("2026-11-02T18:00")));
	}

	@Test
	void breakCoveringTheWholeShiftLeavesNoWorkedTime() {
		Shift shift = shift("2026-11-02T10:00", "2026-11-02T11:00", "2026-11-02T10:00", "2026-11-02T11:00");

		assertThat(shift.workedIntervals()).isEmpty();
		assertThat(shift.workedMinutes()).isZero();
	}

	@Test
	void endMustBeAfterStart() {
		assertInvalid().isThrownBy(() -> shift("2026-11-02T10:00", "2026-11-02T10:00"));
		assertInvalid().isThrownBy(() -> shift("2026-11-02T10:00", "2026-11-02T09:00"));
	}

	@Test
	void lengthIsAtMost24Hours() {
		assertThat(shift("2026-11-02T10:00", "2026-11-03T10:00").workedMinutes()).isEqualTo(1440);
		assertInvalid().isThrownBy(() -> shift("2026-11-02T10:00", "2026-11-03T10:01"));
	}

	@Test
	void breakIsBothSetOrBothEmpty() {
		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A,
				jst("2026-11-02T10:00"), jst("2026-11-02T18:00"), jst("2026-11-02T13:00"), null));
		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A,
				jst("2026-11-02T10:00"), jst("2026-11-02T18:00"), null, jst("2026-11-02T14:00")));
	}

	@Test
	void breakLiesInsideTheShift() {
		assertInvalid().isThrownBy(() -> shift("2026-11-02T10:00", "2026-11-02T18:00",
				"2026-11-02T09:30", "2026-11-02T10:30"));
		assertInvalid().isThrownBy(() -> shift("2026-11-02T10:00", "2026-11-02T18:00",
				"2026-11-02T17:30", "2026-11-02T18:30"));
		assertInvalid().isThrownBy(() -> shift("2026-11-02T10:00", "2026-11-02T18:00",
				"2026-11-02T13:00", "2026-11-02T13:00"));
	}

	@Test
	void timesWithZeroSecondsAndNanosecondsAreWholeMinutes() {
		Shift shift = new Shift(UUID.randomUUID(), JOB_A, jst("2026-11-02T10:00:00"),
				Instant.parse("2026-11-02T09:00:00.000000000Z"), jst("2026-11-02T13:00:00.000"),
				OffsetDateTime.parse("2026-11-02T14:00:00+09:00").toInstant());

		assertThat(shift.startAt()).isEqualTo(jst("2026-11-02T10:00"));
		assertThat(shift.workedMinutes()).isEqualTo(420);
	}

	@Test
	void timesWithSecondsOrNanosecondsAreRejected() {
		Instant start = jst("2026-11-02T10:00");
		Instant end = jst("2026-11-02T18:00");

		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A, start.plusSeconds(30), end));
		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A, start, end.plusSeconds(1)));
		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A, start.plusNanos(1), end));
		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A, start, end.plusMillis(1)));
		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A, start, end,
				jst("2026-11-02T13:00").plusSeconds(59), jst("2026-11-02T14:00")));
		assertInvalid().isThrownBy(() -> new Shift(UUID.randomUUID(), JOB_A, start, end, jst("2026-11-02T13:00"),
				jst("2026-11-02T14:00").plusNanos(999_999_999)));
	}

	@Test
	void shiftsOverlapWhenTheyShareTimeButNotWhenTheyOnlyTouch() {
		Shift shift = shift("2026-11-02T10:00", "2026-11-02T14:00");

		assertThat(shift.overlaps(shift("2026-11-02T13:59", "2026-11-02T15:00"))).isTrue();
		assertThat(shift.overlaps(shift("2026-11-02T09:00", "2026-11-02T10:01"))).isTrue();
		assertThat(shift.overlaps(shift("2026-11-02T11:00", "2026-11-02T12:00"))).isTrue();
		assertThat(shift.overlaps(shift("2026-11-02T14:00", "2026-11-02T15:00"))).isFalse();
		assertThat(shift.overlaps(shift("2026-11-02T08:00", "2026-11-02T10:00"))).isFalse();
	}

	@Test
	void jobIsRequired() {
		assertInvalid()
			.isThrownBy(() -> new Shift(UUID.randomUUID(), null, jst("2026-11-02T10:00"), jst("2026-11-02T18:00")));
	}

	@Test
	void datesAreTakenInTokyoAndAnEndAtMidnightStaysOnTheStartDate() {
		Shift shift = shift("2026-11-02T20:00", "2026-11-03T00:00");

		assertThat(shift.startDate()).isEqualTo(date("2026-11-02"));
		assertThat(shift.lastDate()).isEqualTo(date("2026-11-02"));
		assertThat(shift("2026-11-02T20:00", "2026-11-03T00:01").lastDate()).isEqualTo(date("2026-11-03"));
	}

}
