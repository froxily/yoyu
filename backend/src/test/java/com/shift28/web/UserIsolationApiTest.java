package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserIsolationApiTest extends ApiTestSupport {

	@Test
	void anotherUsersJobsShiftsAndVacationsAreNotFound() {
		ApiClient alice = signedUp("alice@example.com");
		String aliceJob = createJob(alice, "Cafe", 1200);
		String aliceShift = createShift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T14:00");
		String aliceVacation = createdId(alice.post("/api/vacations", """
				{"startDate": "2026-12-21", "endDate": "2027-01-06"}"""));
		ApiClient bob = signedUp("bob@example.com");
		String bobJob = createJob(bob, "Bar", 1300);

		assertThat(bob.put("/api/jobs/" + aliceJob, versioned(job("Mine now", 9999), 0))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(bob.delete("/api/jobs/" + aliceJob)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(bob.put("/api/shifts/" + aliceShift,
				versioned(shift(bobJob, "2026-11-03T10:00", "2026-11-03T11:00"), 0)))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(bob.delete("/api/shifts/" + aliceShift)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(bob.put("/api/vacations/" + aliceVacation, """
				{"startDate": "2026-12-01", "endDate": "2026-12-02", "version": 0}""")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(bob.delete("/api/vacations/" + aliceVacation)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(alice.get("/api/jobs")).bodyJson().isLenientlyEqualTo("""
				[{"id": "%s", "name": "Cafe", "hourlyWageYen": 1200}]""".formatted(aliceJob));
		assertThat(alice.get("/api/shifts?from=2026-11-02&to=2026-11-02")).bodyJson().isLenientlyEqualTo("""
				[{"id": "%s", "startAt": "2026-11-02T10:00:00+09:00"}]""".formatted(aliceShift));
		assertThat(alice.get("/api/vacations")).bodyJson().isLenientlyEqualTo("""
				[{"id": "%s", "startDate": "2026-12-21"}]""".formatted(aliceVacation));
	}

	@Test
	void anotherUsersArchivedJobCannotBeRestored() {
		ApiClient alice = signedUp("alice@example.com");
		String aliceJob = createJob(alice, "Cafe", 1200);
		createShift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T14:00");
		assertThat(alice.delete("/api/jobs/" + aliceJob)).hasStatus(HttpStatus.NO_CONTENT);
		ApiClient bob = signedUp("bob@example.com");

		assertThat(bob.post("/api/jobs/" + aliceJob + "/restore", "")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(alice.get("/api/jobs?includeArchived=true")).bodyJson().isLenientlyEqualTo("""
				[{"id": "%s", "archived": true}]""".formatted(aliceJob));
	}

	@Test
	void shiftsAndChecksCannotUseAnotherUsersJobOrShift() {
		ApiClient alice = signedUp("alice@example.com");
		String aliceJob = createJob(alice, "Cafe", 1200);
		String aliceShift = createShift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T14:00");
		ApiClient bob = signedUp("bob@example.com");
		String bobJob = createJob(bob, "Bar", 1300);

		assertThat(bob.post("/api/shifts", shift(aliceJob, "2026-11-03T10:00", "2026-11-03T11:00")))
			.hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Job not found.");
		assertThat(bob.post("/api/compliance/check", shift(aliceJob, "2026-11-03T10:00", "2026-11-03T11:00")))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(bob.post("/api/compliance/check", """
				{"jobId": "%s", "startAt": "%s", "endAt": "%s", "excludeShiftId": "%s"}"""
			.formatted(bobJob, time("2026-11-03T10:00"), time("2026-11-03T11:00"), aliceShift)))
			.hasStatus(HttpStatus.NOT_FOUND);
		String bobShift = createShift(bob, bobJob, "2026-11-02T10:00", "2026-11-02T14:00");
		assertThat(bob.put("/api/shifts/" + bobShift,
				versioned(shift(aliceJob, "2026-11-02T10:00", "2026-11-02T14:00"), 0)))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(count("shifts")).isEqualTo(2);
	}

	@Test
	void listsAndReportsOnlyContainTheCurrentUsersData() {
		ApiClient alice = signedUp("alice@example.com");
		String aliceJob = createJob(alice, "Cafe", 1200);
		createShift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T17:00");
		createdId(alice.post("/api/vacations", """
				{"startDate": "2026-11-01", "endDate": "2026-11-30"}"""));
		ApiClient bob = signedUp("bob@example.com");

		assertThat(bob.get("/api/jobs?includeArchived=true")).bodyJson().isLenientlyEqualTo("[]");
		assertThat(bob.get("/api/shifts?from=2026-11-01&to=2026-11-30")).bodyJson().isLenientlyEqualTo("[]");
		assertThat(bob.get("/api/vacations")).bodyJson().isLenientlyEqualTo("[]");
		assertThat(bob.get("/api/pay?from=2026-11-01&to=2026-11-30")).bodyJson().isLenientlyEqualTo("""
				{"jobs": [], "totalYen": 0}""");
		assertThat(bob.get("/api/compliance?from=2026-11-02&to=2026-11-02")).bodyJson().isLenientlyEqualTo("""
				{"days": [{"date": "2026-11-02", "minutes": 0, "vacation": false, "status": "OK"}], "issues": []}""");
	}

}
