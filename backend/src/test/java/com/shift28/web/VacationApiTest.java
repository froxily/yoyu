package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class VacationApiTest extends ApiTestSupport {

	@Test
	void createsListsUpdatesAndDeletesVacations() {
		ApiClient alice = signedUp("alice@example.com");
		String winter = createdId(alice.post("/api/vacations", vacation("2026-12-21", "2027-01-06", "Winter")));
		String summer = createdId(alice.post("/api/vacations", """
				{"startDate": "2026-08-01", "endDate": "2026-09-20"}"""));

		assertThat(alice.get("/api/vacations")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				[{"id": "%s", "startDate": "2026-08-01", "endDate": "2026-09-20", "label": null},
				 {"id": "%s", "startDate": "2026-12-21", "endDate": "2027-01-06", "label": "Winter"}]"""
			.formatted(summer, winter));

		assertThat(alice.put("/api/vacations/" + winter,
				versioned(vacation("2026-12-23", "2027-01-07", "Winter break"), 0)))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": "%s", "startDate": "2026-12-23", "endDate": "2027-01-07", "label": "Winter break"}"""
				.formatted(winter));

		assertThat(alice.delete("/api/vacations/" + summer)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(alice.get("/api/vacations")).bodyJson().extractingPath("$[*].id").asArray().containsExactly(winter);
	}

	@Test
	void invalidVacationsAreRejected() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.post("/api/vacations", vacation("2026-12-24", "2026-12-23", "Backwards")))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("a vacation period must not end before it starts");
		assertThat(alice.post("/api/vacations", """
				{"endDate": "2026-12-23"}""")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("startDate");
		assertThat(alice.post("/api/vacations", vacation("2026-12-21", "2027-01-06", "x".repeat(101))))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(count("vacation_periods")).isZero();
	}

	private static String vacation(String startDate, String endDate, String label) {
		return """
				{"startDate": "%s", "endDate": "%s", "label": "%s"}""".formatted(startDate, endDate, label);
	}

}
