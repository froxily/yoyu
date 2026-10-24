package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OptimisticLockingApiTest extends ApiTestSupport {

	@Test
	@DisplayName("L1: a stale shift edit is rejected with 409 and changes nothing")
	void staleShiftEditsAreRejected() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String id = createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");
		assertThat(alice.put("/api/shifts/" + id, versioned(shift(cafe, "2026-11-02T10:00", "2026-11-02T13:00"), 0)))
			.hasStatusOk();

		assertThat(alice.put("/api/shifts/" + id, versioned(shift(cafe, "2026-11-02T10:00", "2026-11-02T14:00"), 0)))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"detail": "This shift was changed elsewhere. Reload and try again.", "stale": true}""");
		assertThat(alice.get("/api/shifts?from=2026-11-02&to=2026-11-02")).bodyJson()
			.extractingPath("$[0].endAt")
			.isEqualTo("2026-11-02T13:00:00+09:00");
	}

	@Test
	@DisplayName("L1: stale job and vacation edits are rejected with 409")
	void staleJobAndVacationEditsAreRejected() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String winter = createdId(alice.post("/api/vacations", """
				{"startDate": "2026-12-21", "endDate": "2027-01-06"}"""));
		assertThat(alice.put("/api/jobs/" + cafe, versioned(job("Cafe Nova", 1250), 0))).hasStatusOk();
		assertThat(alice.put("/api/vacations/" + winter, """
				{"startDate": "2026-12-22", "endDate": "2027-01-06", "version": 0}""")).hasStatusOk();

		assertThat(alice.put("/api/jobs/" + cafe, versioned(job("Old name", 1000), 0)))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("This job was changed elsewhere. Reload and try again.");
		assertThat(alice.put("/api/vacations/" + winter, """
				{"startDate": "2026-12-01", "endDate": "2027-01-06", "version": 0}"""))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("This vacation period was changed elsewhere. Reload and try again.");
		assertThat(alice.get("/api/jobs")).bodyJson().extractingPath("$[0].name").isEqualTo("Cafe Nova");
		assertThat(alice.get("/api/vacations")).bodyJson().extractingPath("$[0].startDate").isEqualTo("2026-12-22");
	}

	@Test
	@DisplayName("L2: every response carries the version and a current edit increments it")
	void currentEditsIncrementTheVersion() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String id = createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");

		assertThat(alice.get("/api/jobs")).bodyJson().extractingPath("$[0].version").isEqualTo(0);
		assertThat(alice.get("/api/shifts?from=2026-11-02&to=2026-11-02")).bodyJson()
			.extractingPath("$[0].version")
			.isEqualTo(0);
		assertThat(alice.put("/api/shifts/" + id, versioned(shift(cafe, "2026-11-02T10:00", "2026-11-02T13:00"), 0)))
			.bodyJson()
			.extractingPath("$.shift.version")
			.isEqualTo(1);
		assertThat(alice.put("/api/jobs/" + cafe, versioned(job("Cafe Nova", 1250), 0))).bodyJson()
			.extractingPath("$.version")
			.isEqualTo(1);
		assertThat(alice.post("/api/vacations", """
				{"startDate": "2026-12-21", "endDate": "2027-01-06"}""")).bodyJson()
			.extractingPath("$.version")
			.isEqualTo(0);
	}

	@Test
	@DisplayName("L3: an edit without a version is rejected with the field that is missing")
	void editsNeedAVersion() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String id = createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");
		String winter = createdId(alice.post("/api/vacations", """
				{"startDate": "2026-12-21", "endDate": "2027-01-06"}"""));

		for (var request : new String[][] { { "/api/shifts/" + id, shift(cafe, "2026-11-02T10:00", "2026-11-02T13:00") },
				{ "/api/jobs/" + cafe, job("Cafe Nova", 1250) }, { "/api/vacations/" + winter, """
						{"startDate": "2026-12-22", "endDate": "2027-01-06"}""" } }) {
			assertThat(alice.put(request[0], request[1])).as(request[0])
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.errors[*].field")
				.asArray()
				.containsExactly("version");
		}
	}

	@Test
	@DisplayName("L5: archiving and restoring a job increment its version")
	void archiveAndRestoreIncrementTheVersion() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");

		assertThat(alice.delete("/api/jobs/" + cafe)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(alice.get("/api/jobs?includeArchived=true")).bodyJson()
			.extractingPath("$[0].version")
			.isEqualTo(1);
		assertThat(alice.post("/api/jobs/" + cafe + "/restore", "")).bodyJson()
			.extractingPath("$.version")
			.isEqualTo(2);
	}

}
