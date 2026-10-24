package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ShiftApiTest extends ApiTestSupport {

	@Test
	void createsAShiftAndReturnsItWithItsComplianceResult() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);

		MvcTestResult created = alice.post("/api/shifts", """
				{"jobId": "%s", "startAt": "2026-11-02T10:00:00+09:00", "endAt": "2026-11-02T18:00:00+09:00",
				 "breakStart": "2026-11-02T13:00:00+09:00", "breakEnd": "2026-11-02T14:00:00+09:00", "note": "lunch rush"}"""
			.formatted(cafe));

		assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{"shift": {"jobId": "%s", "startAt": "2026-11-02T10:00:00+09:00", "endAt": "2026-11-02T18:00:00+09:00",
				           "breakStart": "2026-11-02T13:00:00+09:00", "breakEnd": "2026-11-02T14:00:00+09:00",
				           "note": "lunch rush", "workedMinutes": 420},
				 "compliance": {"status": "OK", "issues": []}}""".formatted(cafe));
	}

	@Test
	void instantsWithAnyOffsetAreStoredAndReturnedInTokyoTime() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);

		assertThat(alice.post("/api/shifts", """
				{"jobId": "%s", "startAt": "2026-11-02T19:30:00+05:30", "endAt": "2026-11-02T16:00:00Z"}"""
			.formatted(cafe))).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"shift": {"startAt": "2026-11-02T23:00:00+09:00", "endAt": "2026-11-03T01:00:00+09:00"}}""");
	}

	@Test
	void listsTheShiftsOverlappingTheDateRangeInStartOrder() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String acrossMidnight = createShift(alice, cafe, "2026-11-01T22:00", "2026-11-02T01:00");
		String morning = createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T14:00");
		createShift(alice, cafe, "2026-11-03T10:00", "2026-11-03T12:00");

		assertThat(alice.get("/api/shifts?from=2026-11-02&to=2026-11-02")).hasStatusOk()
			.bodyJson()
			.extractingPath("$[*].id")
			.asArray()
			.containsExactly(acrossMidnight, morning);
	}

	@Test
	void updatesAShiftAndChangesUpdatedAtThenDeletesIt() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String id = createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");
		OffsetDateTime createdUpdatedAt = updatedAt(id);

		assertThat(alice.put("/api/shifts/" + id, versioned(shift(cafe, "2026-11-02T11:00", "2026-11-02T15:00"), 0)))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"shift": {"id": "%s", "startAt": "2026-11-02T11:00:00+09:00", "workedMinutes": 240}}""".formatted(id));
		OffsetDateTime firstUpdate = updatedAt(id);
		assertThat(alice.put("/api/shifts/" + id, versioned(shift(cafe, "2026-11-02T11:00", "2026-11-02T16:00"), 1)))
			.hasStatusOk();

		assertThat(firstUpdate).isAfter(createdUpdatedAt);
		assertThat(updatedAt(id)).isAfter(firstUpdate);

		assertThat(alice.delete("/api/shifts/" + id)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("shifts")).isZero();
	}

	@Test
	@DisplayName("T16 through the API: an overlapping shift is a 409, a touching one is accepted")
	void overlappingShiftsAreRejected() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String bar = createJob(alice, "Bar", 1300);
		createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T14:00");

		assertThat(alice.post("/api/shifts", shift(bar, "2026-11-02T13:00", "2026-11-02T15:00")))
			.hasStatus(HttpStatus.CONFLICT)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("This shift overlaps another shift.");
		String touching = createShift(alice, bar, "2026-11-02T14:00", "2026-11-02T16:00");
		assertThat(alice.put("/api/shifts/" + touching, versioned(shift(bar, "2026-11-02T13:59", "2026-11-02T16:00"), 0)))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(count("shifts")).isEqualTo(2);
	}

	@Test
	@DisplayName("T20 through the API: end before start, 24 h 1 min and a break outside the shift are each a 400")
	void invalidShiftsAreRejected() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);

		assertBadRequest(alice, shift(cafe, "2026-11-02T10:00", "2026-11-02T09:00"), "a shift must end after it starts");
		assertBadRequest(alice, shift(cafe, "2026-11-02T10:00", "2026-11-03T10:01"), "a shift lasts at most 24 hours");
		assertBadRequest(alice,
				shift(cafe, "2026-11-02T10:00", "2026-11-02T18:00", "2026-11-02T09:00", "2026-11-02T11:00"),
				"a break must lie inside its shift");
		assertBadRequest(alice, """
				{"jobId": "%s", "startAt": "2026-11-02T10:00:30+09:00", "endAt": "2026-11-02T12:00:00+09:00"}"""
			.formatted(cafe), "shift times must be whole minutes");
		assertThat(alice.post("/api/shifts", """
				{"jobId": "%s", "endAt": "2026-11-02T12:00:00+09:00"}""".formatted(cafe)))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("startAt");
		assertThat(alice.post("/api/shifts", """
				{"jobId": "%s", "startAt": "2026-11-02T10:00:00", "endAt": "2026-11-02T12:00:00"}""".formatted(cafe)))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(count("shifts")).isZero();
	}

	@Test
	void aShiftThatAlreadyHappenedIsSavedEvenWhenItBreaksTheLimit() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-05", "10:00", "17:00");

		MvcTestResult saved = alice.post("/api/shifts", shift(cafe, "2026-11-06T10:00", "2026-11-06T10:01"));

		assertThat(saved).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.compliance.status")
			.isEqualTo("VIOLATION");
		assertThat(saved).bodyJson()
			.extractingPath("$.compliance.issues[*].start")
			.asArray()
			.containsExactly("2026-10-31", "2026-11-01", "2026-11-02");
		List<String> ids = shiftIdsInStartOrder();
		assertThat(saved).bodyJson().extractingPath("$.compliance.issues[0]").isEqualTo(Map.of("type", "WINDOW",
				"start", "2026-10-31", "end", "2026-11-06", "minutes", 1681, "limitMinutes", 1680, "status", "VIOLATION",
				"attribution", "CALENDAR_SPLIT", "contributions", List.of(
					contribution(ids.get(0), cafe, "Cafe", "2026-11-02T10:00:00+09:00", 420, false),
					contribution(ids.get(1), cafe, "Cafe", "2026-11-03T10:00:00+09:00", 420, false),
					contribution(ids.get(2), cafe, "Cafe", "2026-11-04T10:00:00+09:00", 420, false),
					contribution(ids.get(3), cafe, "Cafe", "2026-11-05T10:00:00+09:00", 420, false),
					contribution(null, cafe, "Cafe", "2026-11-06T10:00:00+09:00", 1, true))));
	}

	@Test
	void aFutureShiftThatBreaksTheLimitNeedsConfirmation() {
		clock.set(jst("2026-11-01T09:00"));
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");

		MvcTestResult unconfirmed = alice.post("/api/shifts", shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00"));

		assertThat(unconfirmed).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.compliance.status")
			.isEqualTo("VIOLATION");
		assertThat(unconfirmed).bodyJson()
			.extractingPath("$.compliance.issues[*].minutes")
			.asArray()
			.isNotEmpty()
			.containsOnly(1740);
		assertThat(count("shifts")).isEqualTo(3);

		assertThat(alice.post("/api/shifts", """
				{"jobId": "%s", "startAt": "%s", "endAt": "%s", "confirmed": true}"""
			.formatted(cafe, time("2026-11-05T10:00"), time("2026-11-05T18:00")))).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.compliance.status")
			.isEqualTo("VIOLATION");
		assertThat(count("shifts")).isEqualTo(4);
	}

	@Test
	void aFutureShiftWithinTheLimitNeedsNoConfirmation() {
		clock.set(jst("2026-11-01T09:00"));
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);

		assertThat(alice.post("/api/shifts", shift(cafe, "2026-11-02T10:00", "2026-11-02T17:00")))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void editingAFutureShiftSoItBreaksTheLimitNeedsConfirmation() {
		clock.set(jst("2026-11-01T09:00"));
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");
		String thursday = createShift(alice, cafe, "2026-11-05T10:00", "2026-11-05T17:00");

		MvcTestResult unconfirmed = alice.put("/api/shifts/" + thursday,
				versioned(shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00"), 0));

		assertThat(unconfirmed).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.compliance.status")
			.isEqualTo("VIOLATION");
		assertThat(unconfirmed).bodyJson()
			.extractingPath("$.compliance.issues[*].minutes")
			.asArray()
			.isNotEmpty()
			.containsOnly(1740);
		assertThat(endAt(thursday)).isEqualTo(jst("2026-11-05T17:00"));

		assertThat(alice.put("/api/shifts/" + thursday, """
				{"jobId": "%s", "startAt": "%s", "endAt": "%s", "confirmed": true, "version": 0}"""
			.formatted(cafe, time("2026-11-05T10:00"), time("2026-11-05T18:00")))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.compliance.status")
			.isEqualTo("VIOLATION");
		assertThat(endAt(thursday)).isEqualTo(jst("2026-11-05T18:00"));
	}

	@Test
	void editingAShiftThatAlreadyHappenedSoItBreaksTheLimitSavesIt() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");
		String thursday = createShift(alice, cafe, "2026-11-05T10:00", "2026-11-05T17:00");

		assertThat(alice.put("/api/shifts/" + thursday, versioned(shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00"), 0)))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.compliance.status")
			.isEqualTo("VIOLATION");
		assertThat(endAt(thursday)).isEqualTo(jst("2026-11-05T18:00"));
	}

	@Test
	void aShiftThatEndsWhenItStartsIsRejected() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);

		assertBadRequest(alice, shift(cafe, "2026-11-02T10:00", "2026-11-02T10:00"), "a shift must end after it starts");
		assertThat(count("shifts")).isZero();
	}

	private Instant endAt(String shiftId) {
		return jdbc.sql("SELECT end_at FROM shifts WHERE id = ?::uuid")
			.param(shiftId)
			.query((rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant())
			.single();
	}

	private OffsetDateTime updatedAt(String shiftId) {
		return jdbc.sql("SELECT updated_at FROM shifts WHERE id = ?::uuid")
			.param(shiftId)
			.query((rs, row) -> rs.getObject(1, OffsetDateTime.class))
			.single();
	}

	private static void assertBadRequest(ApiClient client, String body, String detail) {
		assertThat(client.post("/api/shifts", body)).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo(detail);
	}

}
