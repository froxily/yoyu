package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleVersionApiTest extends ApiTestSupport {

	@AfterEach
	void removeAddedVersions() {
		jdbc.sql("DELETE FROM rule_versions WHERE effective_from <> DATE '2000-01-01'").update();
	}

	@Test
	@DisplayName("T24 through the API: a window across a rule change reports the stricter limit and the versions")
	void aWindowAcrossARuleChangeSaysTheRulesChanged() {
		jdbc.sql("""
				INSERT INTO rule_versions (effective_from, regular_window_limit_minutes, vacation_window_limit_minutes,
				  vacation_day_limit_minutes, source_url) VALUES (DATE '2026-11-09', 1440, 2400, 480, 'https://example.com')""")
			.update();
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-06", "2026-11-08", "10:00", "17:00");
		createShift(alice, cafe, "2026-11-09T10:00", "2026-11-09T13:01");

		assertThat(alice.get("/api/compliance?from=2026-11-03&to=2026-11-03")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"issues": [{"type": "WINDOW", "start": "2026-11-03", "end": "2026-11-09", "minutes": 1441,
					             "limitMinutes": 1440, "status": "VIOLATION", "rulesChanged": true,
					             "ruleVersions": ["2000-01-01", "2026-11-09"]}]}""");
	}

	@Test
	void issuesUnderOneRuleVersionCarryNoRuleChangeFields() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-05", "10:00", "17:00");
		createShift(alice, cafe, "2026-11-06T10:00", "2026-11-06T10:01");

		assertThat(alice.get("/api/compliance?from=2026-11-02&to=2026-11-02")).bodyJson()
			.extractingPath("$.issues[0]")
			.asMap()
			.containsEntry("status", "VIOLATION")
			.doesNotContainKeys("rulesChanged", "ruleVersions");
	}

}
