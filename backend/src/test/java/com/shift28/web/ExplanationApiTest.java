package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ExplanationApiTest extends ApiTestSupport {

	@Test
	@DisplayName("E5: a draft check lists the existing shifts by job and the draft itself (T14)")
	void aDraftCheckExplainsItsIssues() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String bookshop = createJob(alice, "Bookshop", 1100);
		String monday = createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T17:00");
		String tuesday = createShift(alice, bookshop, "2026-11-03T10:00", "2026-11-03T17:00");
		String wednesday = createShift(alice, cafe, "2026-11-04T10:00", "2026-11-04T17:00");

		MvcTestResult check = alice.post("/api/compliance/check", shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00"));

		assertThat(check).hasStatusOk().bodyJson().extractingPath("$.issues[*].start").asArray()
			.containsExactly("2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02");
		List<Map<String, Object>> contributions = ApiClient.read(check, "$.issues[3].contributions");
		assertThat(contributions).containsExactly(
				contribution(monday, cafe, "Cafe", "2026-11-02T10:00:00+09:00", 420, false),
				contribution(tuesday, bookshop, "Bookshop", "2026-11-03T10:00:00+09:00", 420, false),
				contribution(wednesday, cafe, "Cafe", "2026-11-04T10:00:00+09:00", 420, false),
				contribution(null, cafe, "Cafe", "2026-11-05T10:00:00+09:00", 480, true));
	}

	@Test
	@DisplayName("E6: contributions from an archived job still carry its name")
	void archivedJobsAreNamed() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-05", "10:00", "17:00");
		createShift(alice, cafe, "2026-11-06T10:00", "2026-11-06T10:01");
		assertThat(alice.delete("/api/jobs/" + cafe)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(alice.get("/api/compliance?from=2026-11-02&to=2026-11-02")).bodyJson()
			.extractingPath("$.issues[0].contributions[*].jobName")
			.asArray()
			.containsExactly("Cafe", "Cafe", "Cafe", "Cafe", "Cafe");
	}

	@Test
	void savingAFutureShiftThatNeedsConfirmationExplainsWhy() {
		clock.set(jst("2026-11-01T09:00"));
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");

		assertThat(alice.post("/api/shifts", shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00")))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.extractingPath("$.compliance.issues[0].contributions[*].minutes")
			.asArray()
			.containsExactly(420, 420, 420, 480);
	}

}
