package com.shift28.domain;

import static com.shift28.domain.Fixtures.JOB_A;
import static com.shift28.domain.Fixtures.JOB_B;
import static com.shift28.domain.Fixtures.assertInvalid;
import static com.shift28.domain.Fixtures.date;
import static com.shift28.domain.Fixtures.shift;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayCalculatorTest {

	private static PayEstimate november(List<Shift> shifts, Map<UUID, JobRate> rates) {
		return PayCalculator.estimate(shifts, rates, date("2026-11-01"), date("2026-11-30"));
	}

	@Test
	@DisplayName("T17: wage 1200, Nov 2 20:00 → Nov 3 00:00 pays 2 h × 1200 + 2 h × 1500 = 5400 yen")
	void t17LateNightPremium() {
		PayEstimate estimate = november(List.of(shift("2026-11-02T20:00", "2026-11-03T00:00")),
				Map.of(JOB_A, new JobRate(1200, 0)));

		assertThat(estimate.totalYen()).isEqualTo(5400);
		assertThat(estimate.shifts()).singleElement().satisfies(pay -> {
			assertThat(pay.minutes()).isEqualTo(new PayMinutes(120, 120, 0, 0));
			assertThat(pay.totalYen()).isEqualTo(5400);
		});
	}

	@Test
	@DisplayName("T18: wage 1000, Nov 2 13:00 → Nov 3 00:00 with break 17:00–18:00 pays 8000 + 3000 = 11000 yen")
	void t18OvertimeDuringLateNight() {
		PayEstimate estimate = november(
				List.of(shift("2026-11-02T13:00", "2026-11-03T00:00", "2026-11-02T17:00", "2026-11-02T18:00")),
				Map.of(JOB_A, new JobRate(1000, 0)));

		assertThat(estimate.totalYen()).isEqualTo(11000);
		assertThat(estimate.shifts()).singleElement()
			.satisfies(pay -> assertThat(pay.minutes()).isEqualTo(new PayMinutes(480, 0, 0, 120)));
	}

	@Test
	@DisplayName("T19: wage 1000, Nov 7 18:00 → Nov 8 04:00 pays 4000 + 5000 + 3000 = 12000 yen")
	void t19OvertimeAcrossMidnightBelongsToTheStartDay() {
		PayEstimate estimate = november(List.of(shift("2026-11-07T18:00", "2026-11-08T04:00")),
				Map.of(JOB_A, new JobRate(1000, 0)));

		assertThat(estimate.totalYen()).isEqualTo(12000);
		assertThat(estimate.shifts()).singleElement()
			.satisfies(pay -> assertThat(pay.minutes()).isEqualTo(new PayMinutes(240, 240, 0, 120)));
	}

	@Test
	void overtimeAccumulatesAcrossTheSameJobsShiftsOnOneWorkDay() {
		PayEstimate estimate = november(
				List.of(shift("2026-11-02T15:00", "2026-11-02T19:00"), shift("2026-11-02T09:00", "2026-11-02T14:00")),
				Map.of(JOB_A, new JobRate(1000, 0)));

		assertThat(estimate.shifts()).extracting(ShiftPay::minutes)
			.containsExactly(new PayMinutes(300, 0, 0, 0), new PayMinutes(180, 0, 60, 0));
		assertThat(estimate.shifts()).extracting(ShiftPay::totalYen).containsExactly(5000L, 4250L);
		assertThat(estimate.totalYen()).isEqualTo(9250);
	}

	@Test
	void overtimeIsCountedPerJob() {
		PayEstimate estimate = november(
				List.of(shift(JOB_A, "2026-11-02T09:00", "2026-11-02T14:00"),
						shift(JOB_B, "2026-11-02T15:00", "2026-11-02T20:00")),
				Map.of(JOB_A, new JobRate(1000, 0), JOB_B, new JobRate(1000, 0)));

		assertThat(estimate.jobs()).extracting(JobPay::jobId).containsExactly(JOB_A, JOB_B);
		assertThat(estimate.jobs()).extracting(JobPay::minutes)
			.containsExactly(new PayMinutes(300, 0, 0, 0), new PayMinutes(300, 0, 0, 0));
		assertThat(estimate.totalYen()).isEqualTo(10000);
	}

	@Test
	void transportIsAddedOncePerShift() {
		PayEstimate estimate = november(
				List.of(shift("2026-11-02T10:00", "2026-11-02T12:00"), shift("2026-11-03T10:00", "2026-11-03T12:00")),
				Map.of(JOB_A, new JobRate(1000, 500)));

		assertThat(estimate.shifts()).extracting(ShiftPay::totalYen).containsExactly(2500L, 2500L);
		assertThat(estimate.jobs()).singleElement().satisfies(job -> {
			assertThat(job.minutes()).isEqualTo(new PayMinutes(240, 0, 0, 0));
			assertThat(job.workPayYen()).isEqualTo(4000);
			assertThat(job.transportYen()).isEqualTo(1000);
			assertThat(job.totalYen()).isEqualTo(5000);
		});
		assertThat(estimate.totalYen()).isEqualTo(5000);
	}

	@Test
	void roundsHalfUp() {
		PayEstimate estimate = november(List.of(shift("2026-11-02T10:00", "2026-11-02T10:01")),
				Map.of(JOB_A, new JobRate(1050, 0)));

		assertThat(estimate.totalYen()).isEqualTo(18);
	}

	@Test
	void roundsOncePerShiftNotPerCategory() {
		PayEstimate estimate = november(List.of(shift("2026-11-02T21:59", "2026-11-02T22:01")),
				Map.of(JOB_A, new JobRate(1050, 0)));

		assertThat(estimate.shifts()).singleElement()
			.satisfies(pay -> assertThat(pay.minutes()).isEqualTo(new PayMinutes(1, 1, 0, 0)));
		assertThat(estimate.totalYen()).isEqualTo(39);
	}

	@Test
	void lateNightEndsAtFiveInTheMorning() {
		PayEstimate estimate = november(List.of(shift("2026-11-02T04:00", "2026-11-02T06:00")),
				Map.of(JOB_A, new JobRate(1000, 0)));

		assertThat(estimate.shifts()).singleElement()
			.satisfies(pay -> assertThat(pay.minutes()).isEqualTo(new PayMinutes(60, 60, 0, 0)));
		assertThat(estimate.totalYen()).isEqualTo(2250);
	}

	@Test
	void rangeSelectsShiftsByStartDate() {
		Shift startsInOctober = shift("2026-10-31T23:00", "2026-11-01T03:00");
		Shift startsOnNovember30 = shift("2026-11-30T23:00", "2026-12-01T02:00");

		PayEstimate estimate = november(List.of(startsInOctober, startsOnNovember30),
				Map.of(JOB_A, new JobRate(1000, 0)));

		assertThat(estimate.shifts()).extracting(ShiftPay::shiftId).containsExactly(startsOnNovember30.id());
	}

	@Test
	void everyJobNeedsARate() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> november(List.of(shift(JOB_B, "2026-11-02T10:00", "2026-11-02T12:00")), Map.of(JOB_A, new JobRate(1000, 0))));
	}

	@Test
	void wageIsPositiveAndTransportIsNotNegative() {
		assertInvalid().isThrownBy(() -> new JobRate(0, 0));
		assertInvalid().isThrownBy(() -> new JobRate(1000, -1));
	}

}
