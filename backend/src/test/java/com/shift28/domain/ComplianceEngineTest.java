package com.shift28.domain;

import static com.shift28.domain.Attribution.CALENDAR_SPLIT;
import static com.shift28.domain.Attribution.START_DAY;
import static com.shift28.domain.Fixtures.JOB_A;
import static com.shift28.domain.Fixtures.JOB_B;
import static com.shift28.domain.Fixtures.WINTER;
import static com.shift28.domain.Fixtures.all;
import static com.shift28.domain.Fixtures.daily;
import static com.shift28.domain.Fixtures.date;
import static com.shift28.domain.Fixtures.jst;
import static com.shift28.domain.Fixtures.shift;
import static com.shift28.domain.Status.AMBIGUOUS;
import static com.shift28.domain.Status.OK;
import static com.shift28.domain.Status.VIOLATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComplianceEngineTest {

	private static ComplianceEngine engine(List<Shift> shifts) {
		return new ComplianceEngine(shifts, List.of());
	}

	private static ComplianceEngine vacationEngine(List<Shift> shifts) {
		return new ComplianceEngine(shifts, List.of(WINTER));
	}

	private static Check window(ComplianceReport report, String start) {
		return report.window(date(start)).orElseThrow();
	}

	@Test
	@DisplayName("T1: Nov 2–5 10:00–17:00 keeps every window OK with a maximum of 1680")
	void t1FourSevenHourDaysAreWithinTheLimit() {
		ComplianceReport report = engine(daily("2026-11-02", "2026-11-05", "10:00", "17:00"))
			.evaluate(date("2026-11-02"), date("2026-11-05"));

		assertThat(report.windows()).isNotEmpty().allSatisfy(window -> assertThat(window.status()).isEqualTo(OK));
		assertThat(report.windows().stream().mapToLong(window -> window.reported().minutes()).max())
			.hasValue(1680);
		assertThat(report.worstStatus()).isEqualTo(OK);
		assertThat(report.issues()).isEmpty();
	}

	@Test
	@DisplayName("T2: one more minute on Nov 6 puts every window containing Nov 2–6 at 1681, VIOLATION")
	void t2OneMinuteOverTheLimit() {
		ComplianceReport report = engine(all(daily("2026-11-02", "2026-11-05", "10:00", "17:00"),
				List.of(shift("2026-11-06T10:00", "2026-11-06T10:01"))))
			.evaluate(date("2026-11-02"), date("2026-11-06"));

		List<Check> violations = report.windows().stream().filter(window -> window.status() == VIOLATION).toList();
		assertThat(violations).extracting(Check::start)
			.containsExactly(date("2026-10-31"), date("2026-11-01"), date("2026-11-02"));
		assertThat(violations).allSatisfy(window -> assertThat(window.reported().minutes()).isEqualTo(1681));
		assertThat(report.worstStatus()).isEqualTo(VIOLATION);
	}

	@Test
	@DisplayName("T3: calendar weeks look fine but the rolling window Nov 6–12 holds 1740, VIOLATION")
	void t3RollingWindowCatchesWhatCalendarWeeksMiss() {
		ComplianceReport report = engine(all(daily("2026-11-06", "2026-11-09", "10:00", "17:00"),
				List.of(shift("2026-11-10T10:00", "2026-11-10T11:00"))))
			.evaluate(date("2026-11-02"), date("2026-11-15"));

		assertThat(window(report, "2026-11-02").reported().minutes()).isEqualTo(1260);
		assertThat(window(report, "2026-11-02").status()).isEqualTo(OK);
		assertThat(window(report, "2026-11-09").reported().minutes()).isEqualTo(480);
		assertThat(window(report, "2026-11-09").status()).isEqualTo(OK);
		assertThat(window(report, "2026-11-06").end()).isEqualTo(date("2026-11-12"));
		assertThat(window(report, "2026-11-06").reported().minutes()).isEqualTo(1740);
		assertThat(window(report, "2026-11-06").status()).isEqualTo(VIOLATION);
	}

	@Test
	@DisplayName("T4: jobs are combined, so job A Nov 2–5 plus job B Nov 7 make 1740 in Nov 2–8, VIOLATION")
	void t4AllJobsCountTogether() {
		ComplianceReport report = engine(all(daily(JOB_A, "2026-11-02", "2026-11-05", "10:00", "15:00"),
				List.of(shift(JOB_B, "2026-11-07T09:00", "2026-11-07T18:00"))))
			.evaluate(date("2026-11-02"), date("2026-11-08"));

		assertThat(window(report, "2026-11-02").reported().minutes()).isEqualTo(1740);
		assertThat(window(report, "2026-11-02").status()).isEqualTo(VIOLATION);
	}

	@Test
	@DisplayName("T8: 540 minutes on vacation date Dec 22 is a day VIOLATION")
	void t8VacationDayOverEightHours() {
		ComplianceReport report = vacationEngine(List.of(shift("2026-12-22T09:00", "2026-12-22T18:00")))
			.evaluate(date("2026-12-22"), date("2026-12-22"));

		Check day = report.day(date("2026-12-22")).orElseThrow();
		assertThat(day.type()).isEqualTo(CheckType.DAY);
		assertThat(day.status()).isEqualTo(VIOLATION);
		assertThat(day.reported().minutes()).isEqualTo(540);
		assertThat(day.reported().limitMinutes()).isEqualTo(480);
	}

	@Test
	@DisplayName("T9: Dec 28 – Jan 1 09:00–17:00 is all OK; Dec 28 – Jan 3 holds 2400 inside the vacation")
	void t9FortyHoursInsideTheVacation() {
		ComplianceReport report = vacationEngine(daily("2026-12-28", "2027-01-01", "09:00", "17:00"))
			.evaluate(date("2026-12-28"), date("2027-01-01"));

		assertThat(report.worstStatus()).isEqualTo(OK);
		assertThat(window(report, "2026-12-28").reported().minutes()).isEqualTo(2400);
		assertThat(window(report, "2026-12-28").status()).isEqualTo(OK);
		assertThat(report.windows()).filteredOn(window -> window.reported().minutes() > 1680)
			.isNotEmpty()
			.allSatisfy(window -> assertThat(WINTER.covers(window.start()) && WINTER.covers(window.end())).isTrue());
	}

	@Test
	@DisplayName("T10: one more minute on Jan 2 puts Dec 28 – Jan 3 at 2401, VIOLATION")
	void t10OneMinuteOverTheVacationLimit() {
		ComplianceReport report = vacationEngine(all(daily("2026-12-28", "2027-01-01", "09:00", "17:00"),
				List.of(shift("2027-01-02T09:00", "2027-01-02T09:01"))))
			.evaluate(date("2026-12-28"), date("2027-01-02"));

		assertThat(window(report, "2026-12-28").reported().minutes()).isEqualTo(2401);
		assertThat(window(report, "2026-12-28").status()).isEqualTo(VIOLATION);
	}

	@Test
	@DisplayName("T11: Dec 17–23 mixes regular and vacation dates and holds 2400, AMBIGUOUS and no VIOLATION")
	void t11MixedWindowBetweenTheLimitsIsAmbiguous() {
		ComplianceReport report = vacationEngine(all(daily("2026-12-17", "2026-12-19", "09:00", "17:00"),
				daily("2026-12-21", "2026-12-22", "09:00", "17:00")))
			.evaluate(date("2026-12-17"), date("2026-12-22"));

		assertThat(window(report, "2026-12-17").reported().minutes()).isEqualTo(2400);
		assertThat(window(report, "2026-12-17").status()).isEqualTo(AMBIGUOUS);
		assertThat(report.worstStatus()).isEqualTo(AMBIGUOUS);
	}

	@Test
	@DisplayName("T12: one more hour on Dec 23 puts mixed window Dec 17–23 at 2460, VIOLATION")
	void t12MixedWindowOverFortyHoursIsAViolation() {
		ComplianceReport report = vacationEngine(all(daily("2026-12-17", "2026-12-19", "09:00", "17:00"),
				daily("2026-12-21", "2026-12-22", "09:00", "17:00"), List.of(shift("2026-12-23T09:00", "2026-12-23T10:00"))))
			.evaluate(date("2026-12-17"), date("2026-12-23"));

		assertThat(window(report, "2026-12-17").reported().minutes()).isEqualTo(2460);
		assertThat(window(report, "2026-12-17").status()).isEqualTo(VIOLATION);
	}

	@Test
	@DisplayName("T13: after Nov 2–4 10:00–17:00, capacity is 420 on Nov 5, 420 on Nov 8 and 1680 on Nov 11")
	void t13Capacity() {
		ComplianceEngine engine = engine(daily("2026-11-02", "2026-11-04", "10:00", "17:00"));

		assertThat(engine.remainingMinutes(date("2026-11-05"))).isEqualTo(420);
		assertThat(engine.remainingMinutes(date("2026-11-08"))).isEqualTo(420);
		assertThat(engine.remainingMinutes(date("2026-11-11"))).isEqualTo(1680);
	}

	@Test
	@DisplayName("T14: a draft Nov 5 10:00–18:00 after Nov 2–4 is a VIOLATION with window total 1740")
	void t14DraftCheck() {
		ComplianceReport result = engine(daily("2026-11-02", "2026-11-04", "10:00", "17:00"))
			.check(shift("2026-11-05T10:00", "2026-11-05T18:00"));

		assertThat(result.worstStatus()).isEqualTo(VIOLATION);
		assertThat(result.issues()).isNotEmpty().allSatisfy(issue -> {
			assertThat(issue.type()).isEqualTo(CheckType.WINDOW);
			assertThat(issue.status()).isEqualTo(VIOLATION);
			assertThat(issue.reported().minutes()).isEqualTo(1740);
		});
	}

	@Test
	@DisplayName("A draft that edits an existing shift excludes the original")
	void draftEditExcludesTheOriginalShift() {
		List<Shift> shifts = daily("2026-11-02", "2026-11-05", "10:00", "17:00");
		Shift original = shifts.getLast();
		Shift edited = new Shift(original.id(), JOB_A, jst("2026-11-05T10:00"), jst("2026-11-05T16:00"));
		ComplianceEngine engine = engine(shifts);

		assertThat(engine.check(edited, original.id()).worstStatus()).isEqualTo(OK);
		assertThat(engine.check(edited, original.id()).issues()).isEmpty();
		assertThat(engine.check(edited).worstStatus()).isEqualTo(VIOLATION);
	}

	@Test
	@DisplayName("T21: Nov 2–8 is 1680 OK by calendar but 1860 VIOLATION by start day, reported as START_DAY")
	void t21WorseAttributionIsReported() {
		ComplianceReport report = engine(all(daily("2026-11-02", "2026-11-04", "10:00", "17:00"),
				List.of(shift("2026-11-05T10:00", "2026-11-05T16:00"), shift("2026-11-08T23:00", "2026-11-09T03:00"))))
			.evaluate(date("2026-11-02"), date("2026-11-09"));

		Check window = window(report, "2026-11-02");
		assertThat(window.under(CALENDAR_SPLIT).minutes()).isEqualTo(1680);
		assertThat(window.under(CALENDAR_SPLIT).status()).isEqualTo(OK);
		assertThat(window.under(START_DAY).minutes()).isEqualTo(1860);
		assertThat(window.under(START_DAY).status()).isEqualTo(VIOLATION);
		assertThat(window.reported().status()).isEqualTo(VIOLATION);
		assertThat(window.reported().attribution()).isEqualTo(START_DAY);
	}

	@Test
	void regularDatesHaveNoDailyCap() {
		ComplianceReport report = engine(List.of(shift("2026-11-02T08:00", "2026-11-02T20:00")))
			.evaluate(date("2026-11-02"), date("2026-11-02"));

		assertThat(report.days()).isEmpty();
		assertThat(report.worstStatus()).isEqualTo(OK);
	}

	@Test
	void vacationDayCheckUsesTheWorseAttribution() {
		ComplianceReport report = vacationEngine(List.of(shift("2026-12-22T20:00", "2026-12-23T06:00")))
			.evaluate(date("2026-12-22"), date("2026-12-23"));

		Check day = report.day(date("2026-12-22")).orElseThrow();
		assertThat(day.under(CALENDAR_SPLIT).minutes()).isEqualTo(240);
		assertThat(day.under(START_DAY).minutes()).isEqualTo(600);
		assertThat(day.reported().attribution()).isEqualTo(START_DAY);
		assertThat(day.status()).isEqualTo(VIOLATION);
		assertThat(report.day(date("2026-12-23")).orElseThrow().status()).isEqualTo(OK);
	}

	@Test
	void mixedWindowReportsTheLimitItCrossed() {
		List<Shift> ambiguous = all(daily("2026-12-17", "2026-12-19", "09:00", "17:00"),
				daily("2026-12-21", "2026-12-22", "09:00", "17:00"));
		List<Shift> violating = all(ambiguous, List.of(shift("2026-12-23T09:00", "2026-12-23T10:00")));

		assertThat(window(vacationEngine(ambiguous).evaluate(date("2026-12-17"), date("2026-12-17")), "2026-12-17")
			.reported()
			.limitMinutes()).isEqualTo(1680);
		assertThat(window(vacationEngine(violating).evaluate(date("2026-12-17"), date("2026-12-17")), "2026-12-17")
			.reported()
			.limitMinutes()).isEqualTo(2400);
	}

	@Test
	void equalStatusesReportTheHigherTotalThenCalendarSplit() {
		ComplianceReport bothViolating = engine(all(daily("2026-11-02", "2026-11-05", "10:00", "17:00"),
				List.of(shift("2026-11-08T23:00", "2026-11-09T03:00"))))
			.evaluate(date("2026-11-02"), date("2026-11-02"));
		ComplianceReport identical = engine(daily("2026-11-02", "2026-11-05", "10:00", "17:00"))
			.evaluate(date("2026-11-02"), date("2026-11-02"));

		assertThat(window(bothViolating, "2026-11-02").reported())
			.isEqualTo(new Assessment(START_DAY, 1920, 1680, VIOLATION));
		assertThat(window(identical, "2026-11-02").reported())
			.isEqualTo(new Assessment(CALENDAR_SPLIT, 1680, 1680, OK));
	}

	@Test
	void capacityTakesTheStricterAttribution() {
		ComplianceEngine engine = engine(all(daily("2026-11-02", "2026-11-04", "10:00", "17:00"),
				List.of(shift("2026-11-08T23:00", "2026-11-09T03:00"))));

		assertThat(engine.remainingMinutes(date("2026-11-05"))).isEqualTo(180);
	}

	@Test
	void capacityOnAVacationDateIsCappedAtEightHoursPerDay() {
		ComplianceEngine engine = vacationEngine(List.of(shift("2026-12-22T09:00", "2026-12-22T14:00")));

		assertThat(engine.remainingMinutes(date("2026-12-22"))).isEqualTo(180);
		assertThat(engine.remainingMinutes(date("2026-12-24"))).isEqualTo(480);
	}

	@Test
	void capacityUsesTheStrictLimitForMixedWindows() {
		ComplianceEngine engine = vacationEngine(daily("2026-12-17", "2026-12-19", "09:00", "17:00"));

		assertThat(engine.remainingMinutes(date("2026-12-21"))).isEqualTo(240);
	}

	@Test
	void capacityIsNeverNegative() {
		ComplianceEngine engine = engine(all(daily("2026-11-02", "2026-11-05", "10:00", "17:00"),
				List.of(shift("2026-11-06T10:00", "2026-11-06T10:01"))));

		assertThat(engine.remainingMinutes(date("2026-11-06"))).isZero();
	}

	@Test
	void draftOverlapsAreFoundOnAnyJobInStartOrder() {
		Shift evening = shift(JOB_B, "2026-11-02T16:00", "2026-11-02T18:00");
		Shift morning = shift("2026-11-02T10:00", "2026-11-02T14:00");
		ComplianceEngine engine = engine(List.of(evening, morning));

		assertThat(engine.overlapping(shift("2026-11-02T13:00", "2026-11-02T17:00"))).containsExactly(morning, evening);
		assertThat(engine.overlapping(shift("2026-11-02T14:00", "2026-11-02T16:00"))).isEmpty();
	}

	@Test
	void draftOverlapIgnoresTheExcludedShift() {
		Shift morning = shift("2026-11-02T10:00", "2026-11-02T14:00");
		ComplianceEngine engine = engine(List.of(morning));
		Shift moved = new Shift(morning.id(), JOB_A, jst("2026-11-02T11:00"), jst("2026-11-02T15:00"));

		assertThat(engine.overlapping(moved, morning.id())).isEmpty();
		assertThat(engine.overlapping(moved)).containsExactly(morning);
	}

	@Test
	void exposesMinutesPerDateAndVacationDates() {
		ComplianceEngine engine = vacationEngine(List.of(shift("2026-12-20T22:00", "2026-12-21T03:00")));

		assertThat(engine.minutesOn(date("2026-12-20"), CALENDAR_SPLIT)).isEqualTo(120);
		assertThat(engine.minutesOn(date("2026-12-21"), CALENDAR_SPLIT)).isEqualTo(180);
		assertThat(engine.minutesOn(date("2026-12-20"), START_DAY)).isEqualTo(300);
		assertThat(engine.minutesOn(date("2026-12-21"), START_DAY)).isZero();
		assertThat(engine.isVacation(date("2026-12-20"))).isFalse();
		assertThat(engine.isVacation(date("2026-12-21"))).isTrue();
	}

	@Test
	void aWorkedDateTakesTheWorstOfTheWindowsContainingIt() {
		ComplianceEngine engine = engine(all(daily("2026-11-02", "2026-11-05", "10:00", "17:00"),
				List.of(shift("2026-11-06T10:00", "2026-11-06T10:01"))));

		assertThat(engine.statusOn(date("2026-11-02"))).isEqualTo(VIOLATION);
		assertThat(engine.statusOn(date("2026-11-06"))).isEqualTo(VIOLATION);
		assertThat(engine.statusOn(date("2026-11-01"))).isEqualTo(OK);
		assertThat(engine.statusOn(date("2026-11-07"))).isEqualTo(OK);
		assertThat(engine.statusOn(date("2026-11-08"))).isEqualTo(OK);
	}

	@Test
	void aWorkedVacationDateAlsoTakesItsOwnDayRule() {
		ComplianceEngine engine = vacationEngine(List.of(shift("2026-12-22T09:00", "2026-12-22T18:00")));

		assertThat(engine.statusOn(date("2026-12-22"))).isEqualTo(VIOLATION);
		assertThat(engine.statusOn(date("2026-12-21"))).isEqualTo(OK);
		assertThat(engine.statusOn(date("2026-12-23"))).isEqualTo(OK);
	}

	@Test
	void anUnworkedDateInsideAnAmbiguousWindowIsOk() {
		ComplianceEngine engine = vacationEngine(all(daily("2026-12-17", "2026-12-19", "09:00", "17:00"),
				daily("2026-12-21", "2026-12-22", "09:00", "17:00")));

		assertThat(engine.statusOn(date("2026-12-17"))).isEqualTo(AMBIGUOUS);
		assertThat(engine.statusOn(date("2026-12-21"))).isEqualTo(AMBIGUOUS);
		assertThat(engine.statusOn(date("2026-12-20"))).isEqualTo(OK);
	}

	@Test
	void aDateWorkedUnderEitherAttributionCountsAsWorked() {
		ComplianceEngine engine = engine(all(daily("2026-11-02", "2026-11-05", "10:00", "17:00"),
				List.of(shift("2026-11-08T23:00", "2026-11-09T03:00", "2026-11-08T23:00", "2026-11-09T00:00"))));

		assertThat(engine.minutesOn(date("2026-11-08"), CALENDAR_SPLIT)).isZero();
		assertThat(engine.minutesOn(date("2026-11-08"), START_DAY)).isEqualTo(180);
		assertThat(engine.statusOn(date("2026-11-08"))).isEqualTo(VIOLATION);
		assertThat(engine.statusOn(date("2026-11-07"))).isEqualTo(OK);
	}

	@Test
	void draftCheckOnlyReportsWindowsAndDaysTheDraftTouches() {
		ComplianceEngine engine = vacationEngine(all(daily("2026-11-02", "2026-11-05", "10:00", "17:00"),
				List.of(shift("2026-11-06T10:00", "2026-11-06T10:01"))));

		ComplianceReport farAway = engine.check(shift("2026-11-20T10:00", "2026-11-20T11:00"));
		ComplianceReport vacationDay = engine.check(shift("2026-12-22T09:00", "2026-12-22T18:00"));

		assertThat(farAway.worstStatus()).isEqualTo(OK);
		assertThat(farAway.issues()).isEmpty();
		assertThat(vacationDay.issues()).singleElement().satisfies(issue -> {
			assertThat(issue.type()).isEqualTo(CheckType.DAY);
			assertThat(issue.start()).isEqualTo(date("2026-12-22"));
			assertThat(issue.reported().minutes()).isEqualTo(540);
		});
	}

}
