package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ComplianceApiTest extends ApiTestSupport {

	@Test
	@DisplayName("T13 through the API: per-date totals and capacity for today and the next 7 dates")
	void reportsDailyTotalsAndCapacity() {
		clock.set(jst("2026-11-05T08:00"));
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");

		MvcTestResult overview = alice.get("/api/compliance?from=2026-11-02&to=2026-11-04");

		assertThat(overview).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"days": [{"date": "2026-11-02", "minutes": 420, "vacation": false, "status": "OK"},
				          {"date": "2026-11-03", "minutes": 420, "vacation": false, "status": "OK"},
				          {"date": "2026-11-04", "minutes": 420, "vacation": false, "status": "OK"}],
				 "issues": []}""");
		assertThat(overview).bodyJson()
			.extractingPath("$.capacity[*].date")
			.asArray()
			.containsExactly("2026-11-05", "2026-11-06", "2026-11-07", "2026-11-08", "2026-11-09", "2026-11-10",
					"2026-11-11", "2026-11-12");
		assertThat(overview).bodyJson()
			.extractingPath("$.capacity[*].remainingMinutes")
			.asArray()
			.containsExactly(420, 420, 420, 420, 840, 1260, 1680, 1680);
	}

	@Test
	void reportsWindowIssuesAndMarksOnlyTheWorkedDatesInsideThem() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-05", "10:00", "17:00");
		createShift(alice, cafe, "2026-11-06T10:00", "2026-11-06T10:01");

		MvcTestResult overview = alice.get("/api/compliance?from=2026-11-05&to=2026-11-08");

		assertThat(overview).hasStatusOk()
			.bodyJson()
			.extractingPath("$.days[*].status")
			.asArray()
			.containsExactly("VIOLATION", "VIOLATION", "OK", "OK");
		assertThat(overview).bodyJson().extractingPath("$.issues[*].start").asArray().containsExactly("2026-10-31",
				"2026-11-01", "2026-11-02");
		List<String> ids = shiftIdsInStartOrder();
		assertThat(overview).bodyJson().extractingPath("$.issues[2]").isEqualTo(Map.of("type", "WINDOW", "start",
				"2026-11-02", "end", "2026-11-08", "minutes", 1681, "limitMinutes", 1680, "status", "VIOLATION",
				"attribution", "CALENDAR_SPLIT", "contributions", List.of(
					contribution(ids.get(0), cafe, "Cafe", "2026-11-02T10:00:00+09:00", 420, false),
					contribution(ids.get(1), cafe, "Cafe", "2026-11-03T10:00:00+09:00", 420, false),
					contribution(ids.get(2), cafe, "Cafe", "2026-11-04T10:00:00+09:00", 420, false),
					contribution(ids.get(3), cafe, "Cafe", "2026-11-05T10:00:00+09:00", 420, false),
					contribution(ids.get(4), cafe, "Cafe", "2026-11-06T10:00:00+09:00", 1, false))));
	}

	@Test
	@DisplayName("T8 through the API: a vacation day over 8 hours is a DAY issue")
	void vacationDatesAreFlaggedAndCheckedPerDay() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createdId(alice.post("/api/vacations", """
				{"startDate": "2026-12-21", "endDate": "2027-01-06"}"""));
		createShift(alice, cafe, "2026-12-22T09:00", "2026-12-22T18:00");

		MvcTestResult overview = alice.get("/api/compliance?from=2026-12-20&to=2026-12-22");

		assertThat(overview).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"days": [{"date": "2026-12-20", "minutes": 0, "vacation": false, "status": "OK"},
				          {"date": "2026-12-21", "minutes": 0, "vacation": true, "status": "OK"},
				          {"date": "2026-12-22", "minutes": 540, "vacation": true, "status": "VIOLATION"}],
				 "issues": [{"type": "DAY", "start": "2026-12-22", "end": "2026-12-22", "minutes": 540,
				             "limitMinutes": 480, "status": "VIOLATION"}]}""");
	}

	@Test
	@DisplayName("T14 through the API: the draft check reports VIOLATION with window total 1740 and saves nothing")
	void draftCheckReportsTheViolation() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");

		MvcTestResult check = alice.post("/api/compliance/check", shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00"));

		assertThat(check).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("VIOLATION");
		assertThat(check).bodyJson().extractingPath("$.issues[*].type").asArray().isNotEmpty().containsOnly("WINDOW");
		assertThat(check).bodyJson().extractingPath("$.issues[*].minutes").asArray().containsOnly(1740);
		assertThat(count("shifts")).isEqualTo(3);
	}

	@Test
	void draftCheckReportsAnOverlapInsteadOfFailing() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String bar = createJob(alice, "Bar", 1300);
		String morning = createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T14:00");

		MvcTestResult overlapping = alice.post("/api/compliance/check",
				shift(bar, "2026-11-02T13:00", "2026-11-02T15:00"));

		assertThat(overlapping).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"status": "VIOLATION",
				 "issues": [{"type": "OVERLAP", "start": "2026-11-02", "end": "2026-11-02", "status": "VIOLATION",
				             "shiftId": "%s"}]}""".formatted(morning));
		assertThat(overlapping).bodyJson().doesNotHavePath("$.issues[0].minutes");
		assertThat(alice.post("/api/compliance/check", shift(bar, "2026-11-02T14:00", "2026-11-02T16:00")))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"status": "OK", "issues": []}""");
	}

	@Test
	void draftCheckExcludesTheShiftBeingEdited() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");
		String thursday = createShift(alice, cafe, "2026-11-05T10:00", "2026-11-05T17:00");
		String shorter = """
				{"jobId": "%s", "startAt": "%s", "endAt": "%s", "excludeShiftId": "%s"}"""
			.formatted(cafe, time("2026-11-05T10:00"), time("2026-11-05T16:00"), thursday);

		assertThat(alice.post("/api/compliance/check", shorter)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"status": "OK", "issues": []}""");

		MvcTestResult withoutExclusion = alice.post("/api/compliance/check",
				shift(cafe, "2026-11-05T10:00", "2026-11-05T16:00"));
		assertThat(withoutExclusion).bodyJson().extractingPath("$.status").isEqualTo("VIOLATION");
		assertThat(withoutExclusion).bodyJson().extractingPath("$.issues[0].type").isEqualTo("OVERLAP");
		assertThat(withoutExclusion).bodyJson().extractingPath("$.issues[0].shiftId").isEqualTo(thursday);
	}

	@Test
	void draftCheckValidatesItsInput() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);

		assertThat(alice.post("/api/compliance/check", shift(cafe, "2026-11-05T10:00", "2026-11-05T09:00")))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(alice.post("/api/compliance/check",
				shift("00000000-0000-0000-0000-000000000000", "2026-11-05T10:00", "2026-11-05T11:00")))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(alice.post("/api/compliance/check", """
				{"jobId": "%s", "startAt": "%s", "endAt": "%s", "excludeShiftId": "00000000-0000-0000-0000-000000000000"}"""
			.formatted(cafe, time("2026-11-05T10:00"), time("2026-11-05T11:00")))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void dateRangesAreValidated() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.get("/api/compliance?from=2026-11-02")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(alice.get("/api/compliance?from=2026-11-05&to=2026-11-02")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("ordered");
		assertThat(alice.get("/api/compliance?from=2026-01-01&to=2027-01-02")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("withinLimit");
		assertThat(alice.get("/api/compliance?from=2026-01-01&to=2027-01-01")).hasStatusOk();
		assertThat(alice.get("/api/shifts?from=yesterday&to=2026-11-02")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(alice.get("/api/pay?from=2026-11-02")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
