package com.shift28.domain;

import static com.shift28.domain.Attribution.CALENDAR_SPLIT;
import static com.shift28.domain.Attribution.START_DAY;
import static com.shift28.domain.Fixtures.JOB_A;
import static com.shift28.domain.Fixtures.WINTER;
import static com.shift28.domain.Fixtures.all;
import static com.shift28.domain.Fixtures.daily;
import static com.shift28.domain.Fixtures.date;
import static com.shift28.domain.Fixtures.jst;
import static com.shift28.domain.Fixtures.shift;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExplanationTest {

	@Test
	@DisplayName("E1: a window issue lists every shift in it with the minutes it adds (T2)")
	void e1WindowContributions() {
		List<Shift> shifts = all(daily("2026-11-02", "2026-11-05", "10:00", "17:00"),
				List.of(shift("2026-11-06T10:00", "2026-11-06T10:01")));

		Check window = new ComplianceEngine(shifts, List.of()).evaluate(date("2026-11-02"), date("2026-11-02"))
			.window(date("2026-11-02"))
			.orElseThrow();

		assertThat(window.contributions()).extracting(Contribution::shiftId)
			.containsExactlyElementsOf(shifts.stream().map(Shift::id).toList());
		assertThat(window.contributions()).extracting(Contribution::minutes).containsExactly(420, 420, 420, 420, 1);
		assertThat(window.contributions()).extracting(Contribution::jobId).containsOnly(JOB_A);
		assertThat(window.contributions()).noneMatch(Contribution::draft);
		assertThat(sum(window)).isEqualTo(window.reported().minutes());
	}

	@Test
	@DisplayName("E2: under START_DAY an overnight shift counts whole on its start date (T21)")
	void e2StartDayCountsTheWholeOvernightShift() {
		List<Shift> shifts = all(daily("2026-11-02", "2026-11-04", "10:00", "17:00"),
				List.of(shift("2026-11-05T10:00", "2026-11-05T16:00"), shift("2026-11-08T23:00", "2026-11-09T03:00")));

		Check window = new ComplianceEngine(shifts, List.of()).evaluate(date("2026-11-02"), date("2026-11-08"))
			.window(date("2026-11-02"))
			.orElseThrow();

		assertThat(window.reported().attribution()).isEqualTo(START_DAY);
		assertThat(window.contributions()).extracting(Contribution::minutes).containsExactly(420, 420, 420, 360, 240);
		assertThat(sum(window)).isEqualTo(1860);
	}

	@Test
	@DisplayName("E3: under CALENDAR_SPLIT a shift crossing midnight counts only its minutes on that day")
	void e3CalendarSplitCountsOnlyTheMinutesInTheDay() {
		Shift overnight = shift("2026-12-22T20:00", "2026-12-23T04:00");
		Shift day = shift("2026-12-23T09:00", "2026-12-23T15:00");

		Check dec23 = new ComplianceEngine(List.of(overnight, day), List.of(WINTER))
			.evaluate(date("2026-12-23"), date("2026-12-23"))
			.day(date("2026-12-23"))
			.orElseThrow();

		assertThat(dec23.reported().attribution()).isEqualTo(CALENDAR_SPLIT);
		assertThat(dec23.contributions()).extracting(Contribution::shiftId).containsExactly(overnight.id(), day.id());
		assertThat(dec23.contributions()).extracting(Contribution::minutes).containsExactly(240, 360);
		assertThat(dec23.contributions()).extracting(Contribution::startAt)
			.containsExactly(jst("2026-12-22T20:00"), jst("2026-12-23T09:00"));
		assertThat(sum(dec23)).isEqualTo(600);
	}

	@Test
	@DisplayName("E4: checks that are OK carry no contributions")
	void e4OkChecksHaveNoContributions() {
		ComplianceReport report = new ComplianceEngine(daily("2026-11-02", "2026-11-05", "10:00", "17:00"), List.of())
			.evaluate(date("2026-11-02"), date("2026-11-05"));

		assertThat(report.windows()).allSatisfy(window -> assertThat(window.contributions()).isEmpty());
	}

	@Test
	@DisplayName("E5: a checked draft is marked as the draft, whether new or an edit (T14)")
	void e5TheDraftIsMarked() {
		List<Shift> existing = daily("2026-11-02", "2026-11-04", "10:00", "17:00");
		ComplianceEngine engine = new ComplianceEngine(existing, List.of());
		Shift newDraft = new Shift(null, JOB_A, jst("2026-11-05T10:00"), jst("2026-11-05T18:00"));
		UUID edited = existing.get(2).id();
		Shift editDraft = new Shift(edited, JOB_A, jst("2026-11-04T09:00"), jst("2026-11-04T23:30"));

		Check created = engine.check(newDraft).window(date("2026-11-02")).orElseThrow();
		Check changed = engine.check(editDraft, edited).window(date("2026-11-02")).orElseThrow();

		assertThat(created.contributions()).filteredOn(Contribution::draft)
			.singleElement()
			.satisfies(draft -> assertThat(draft.shiftId()).isNull())
			.satisfies(draft -> assertThat(draft.minutes()).isEqualTo(480));
		assertThat(changed.contributions()).filteredOn(Contribution::draft)
			.singleElement()
			.satisfies(draft -> assertThat(draft.shiftId()).isEqualTo(edited))
			.satisfies(draft -> assertThat(draft.minutes()).isEqualTo(870));
		assertThat(changed.contributions()).hasSize(3);
	}

	private static long sum(Check check) {
		return check.contributions().stream().mapToLong(Contribution::minutes).sum();
	}

}
