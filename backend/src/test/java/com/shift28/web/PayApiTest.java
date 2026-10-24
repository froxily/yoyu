package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PayApiTest extends ApiTestSupport {

	private static final String NOVEMBER = "/api/pay?from=2026-11-01&to=2026-11-30";

	@Test
	@DisplayName("T17 through the API: wage 1200, Nov 2 20:00 → Nov 3 00:00 pays 5400 yen")
	void t17LateNightPremium() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createShift(alice, cafe, "2026-11-02T20:00", "2026-11-03T00:00");

		assertThat(alice.get(NOVEMBER)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"from": "2026-11-01", "to": "2026-11-30", "totalYen": 5400,
				 "jobs": [{"jobId": "%s", "name": "Cafe",
				           "minutes": {"normal": 120, "lateNight": 120, "overtime": 0, "overtimeLateNight": 0},
				           "workPayYen": 5400, "transportYen": 0, "totalYen": 5400}]}""".formatted(cafe));
	}

	@Test
	@DisplayName("T18 through the API: wage 1000, Nov 2 13:00 → Nov 3 00:00 with break 17:00–18:00 pays 11000 yen")
	void t18OvertimeDuringLateNight() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1000);
		assertThat(alice.post("/api/shifts",
				shift(cafe, "2026-11-02T13:00", "2026-11-03T00:00", "2026-11-02T17:00", "2026-11-02T18:00")))
			.hasStatus(HttpStatus.CREATED);

		assertThat(alice.get(NOVEMBER)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"totalYen": 11000,
				 "jobs": [{"minutes": {"normal": 480, "lateNight": 0, "overtime": 0, "overtimeLateNight": 120},
				           "totalYen": 11000}]}""");
	}

	@Test
	@DisplayName("T19 through the API: wage 1000, Nov 7 18:00 → Nov 8 04:00 pays 12000 yen")
	void t19OvertimeAcrossMidnight() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1000);
		createShift(alice, cafe, "2026-11-07T18:00", "2026-11-08T04:00");

		assertThat(alice.get(NOVEMBER)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"totalYen": 12000,
				 "jobs": [{"minutes": {"normal": 240, "lateNight": 240, "overtime": 0, "overtimeLateNight": 120},
				           "totalYen": 12000}]}""");
	}

	@Test
	void transportIsAddedPerShiftAndArchivedJobsStillCount() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createdId(alice.post("/api/jobs", """
				{"name": "Cafe", "hourlyWageYen": 1000, "transportYen": 500}"""));
		createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");
		createShift(alice, cafe, "2026-11-03T10:00", "2026-11-03T12:00");
		assertThat(alice.delete("/api/jobs/" + cafe)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(alice.get(NOVEMBER)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"totalYen": 5000, "jobs": [{"workPayYen": 4000, "transportYen": 1000, "totalYen": 5000}]}""");
	}

}
