package com.shift28.domain;

import static com.shift28.domain.Fixtures.WINTER;
import static com.shift28.domain.Fixtures.all;
import static com.shift28.domain.Fixtures.daily;
import static com.shift28.domain.Fixtures.date;
import static com.shift28.domain.Fixtures.shift;
import static com.shift28.domain.Status.OK;
import static com.shift28.domain.Status.VIOLATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleVersionTest {

	private static final RuleSet BASELINE = RuleSet.BASELINE;

	private static final RuleSet B = new RuleSet(date("2026-11-09"), 1440, 2400, 480);

	private static final RuleSet C = new RuleSet(date("2026-12-23"), 1680, 2400, 420);

	private static final List<Shift> T24 = all(daily("2026-11-06", "2026-11-08", "10:00", "17:00"),
			List.of(shift("2026-11-09T10:00", "2026-11-09T13:00")));

	private static final List<Shift> T24_PLUS_ONE = all(T24, List.of(shift("2026-11-09T13:00", "2026-11-09T13:01")));

	private static ComplianceEngine engine(List<Shift> shifts, RuleSet... versions) {
		return new ComplianceEngine(shifts, List.of(), new RuleBook(List.of(versions)));
	}

	@Test
	@DisplayName("Baseline rules equal the SPEC constants")
	void baselineMatchesTheConstants() {
		assertThat(BASELINE).isEqualTo(new RuleSet(date("2000-01-01"), Rules.REGULAR_WINDOW_LIMIT,
				Rules.VACATION_WINDOW_LIMIT, Rules.VACATION_DAY_LIMIT));
	}

	@Test
	@DisplayName("T23: a later rule version never changes windows that end before it takes effect")
	void t23PastResultsAreUnchanged() {
		List<Shift> shifts = daily("2026-11-02", "2026-11-05", "10:00", "17:00");

		Check without = engine(shifts, BASELINE).evaluate(date("2026-11-02"), date("2026-11-08"))
			.window(date("2026-11-02"))
			.orElseThrow();
		Check with = engine(shifts, BASELINE, B).evaluate(date("2026-11-02"), date("2026-11-08"))
			.window(date("2026-11-02"))
			.orElseThrow();

		assertThat(with).isEqualTo(without);
		assertThat(with.reported().minutes()).isEqualTo(1680);
		assertThat(with.status()).isEqualTo(OK);
		assertThat(with.rulesChanged()).isFalse();
	}

	@Test
	@DisplayName("T24: a window spanning a rule change uses the strictest limit and says the rules changed")
	void t24WindowAcrossAChangeUsesTheStrictestLimit() {
		Check atLimit = engine(T24, BASELINE, B).evaluate(date("2026-11-03"), date("2026-11-09"))
			.window(date("2026-11-03"))
			.orElseThrow();
		Check over = engine(T24_PLUS_ONE, BASELINE, B).evaluate(date("2026-11-03"), date("2026-11-09"))
			.window(date("2026-11-03"))
			.orElseThrow();

		assertThat(atLimit.reported().minutes()).isEqualTo(1440);
		assertThat(atLimit.status()).isEqualTo(OK);
		assertThat(over.reported().minutes()).isEqualTo(1441);
		assertThat(over.reported().limitMinutes()).isEqualTo(1440);
		assertThat(over.status()).isEqualTo(VIOLATION);
		assertThat(over.rulesChanged()).isTrue();
		assertThat(over.ruleVersions()).containsExactly(date("2000-01-01"), date("2026-11-09"));
	}

	@Test
	@DisplayName("T25: the same shifts under the baseline rules alone are OK")
	void t25TheVersionAloneFlipsTheResult() {
		Check window = engine(T24_PLUS_ONE, BASELINE).evaluate(date("2026-11-03"), date("2026-11-09"))
			.window(date("2026-11-03"))
			.orElseThrow();

		assertThat(window.reported().minutes()).isEqualTo(1441);
		assertThat(window.status()).isEqualTo(OK);
		assertThat(window.ruleVersions()).containsExactly(date("2000-01-01"));
	}

	@Test
	@DisplayName("T26: each vacation day uses the day limit in effect on that date")
	void t26DayLimitFollowsTheVersionOfEachDate() {
		ComplianceEngine engine = new ComplianceEngine(
				List.of(shift("2026-12-22T09:00", "2026-12-22T16:30"), shift("2026-12-23T09:00", "2026-12-23T16:30")),
				List.of(WINTER), new RuleBook(List.of(BASELINE, C)));

		ComplianceReport report = engine.evaluate(date("2026-12-22"), date("2026-12-23"));

		Check dec22 = report.day(date("2026-12-22")).orElseThrow();
		Check dec23 = report.day(date("2026-12-23")).orElseThrow();
		assertThat(dec22.status()).isEqualTo(OK);
		assertThat(dec22.reported().limitMinutes()).isEqualTo(480);
		assertThat(dec23.status()).isEqualTo(VIOLATION);
		assertThat(dec23.reported().limitMinutes()).isEqualTo(420);
	}

	@Test
	@DisplayName("T27: capacity uses the strictest limit of every window containing the date")
	void t27CapacityUsesTheStrictestLimit() {
		ComplianceEngine engine = engine(List.of(), BASELINE, B);

		assertThat(engine.remainingMinutes(date("2026-11-02"))).isEqualTo(1680);
		assertThat(engine.remainingMinutes(date("2026-11-03"))).isEqualTo(1440);
		assertThat(engine.remainingMinutes(date("2026-11-09"))).isEqualTo(1440);
	}

	@Test
	@DisplayName("T28: dates before the earliest version use the earliest version")
	void t28DatesBeforeTheFirstVersion() {
		RuleBook book = new RuleBook(List.of(B, BASELINE));

		assertThat(book.on(date("1999-12-31"))).isEqualTo(BASELINE);
		assertThat(book.on(date("2026-11-08"))).isEqualTo(BASELINE);
		assertThat(book.on(date("2026-11-09"))).isEqualTo(B);
		assertThat(book.versionsBetween(date("2026-11-03"), date("2026-11-09"))).containsExactly(BASELINE, B);
	}

	@Test
	@DisplayName("T29: a rule book needs at least one version and unique effective dates")
	void t29InvalidRuleBooks() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RuleBook(List.of()));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RuleBook(List.of(BASELINE, new RuleSet(date("2000-01-01"), 1000, 2000, 400))));
		assertThatIllegalArgumentException().isThrownBy(() -> new RuleSet(date("2000-01-01"), 0, 2400, 480));
	}

}
