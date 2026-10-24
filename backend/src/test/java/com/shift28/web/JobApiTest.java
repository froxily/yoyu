package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JobApiTest extends ApiTestSupport {

	@Test
	void creatingAJobWithoutTransportOrColourAppliesTheDefaults() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.post("/api/jobs", """
				{"name": "Cafe", "hourlyWageYen": 1200}""")).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"name": "Cafe", "hourlyWageYen": 1200, "transportYen": 0, "color": "#3b82f6", "archived": false}""");
	}

	@Test
	void createsListsAndUpdatesJobs() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createdId(alice.post("/api/jobs", """
				{"name": "Cafe", "hourlyWageYen": 1200, "transportYen": 300, "color": "#ef4444"}"""));
		createJob(alice, "Bookshop", 1100);

		assertThat(alice.get("/api/jobs")).hasStatusOk()
			.bodyJson()
			.extractingPath("$[*].name")
			.asArray()
			.containsExactly("Bookshop", "Cafe");

		assertThat(alice.put("/api/jobs/" + cafe, """
				{"name": "Cafe Nova", "hourlyWageYen": 1250, "transportYen": 400, "color": "#a855f7", "version": 0}"""))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": "%s", "name": "Cafe Nova", "hourlyWageYen": 1250, "transportYen": 400, "color": "#a855f7"}"""
				.formatted(cafe));
		assertThat(alice.put("/api/jobs/" + cafe, versioned(job("Cafe Nova", 1250), 1))).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"transportYen": 0, "color": "#3b82f6"}""");
	}

	@Test
	void invalidJobsAreRejectedWithTheFieldThatFailed() {
		ApiClient alice = signedUp("alice@example.com");

		assertInvalid(alice, """
				{"name": "Cafe", "hourlyWageYen": 0}""", "hourlyWageYen");
		assertInvalid(alice, """
				{"name": "Cafe"}""", "hourlyWageYen");
		assertInvalid(alice, """
				{"name": "Cafe", "hourlyWageYen": 1200, "transportYen": -1}""", "transportYen");
		assertInvalid(alice, """
				{"name": "Cafe", "hourlyWageYen": 1200, "color": "blue"}""", "color");
		assertInvalid(alice, """
				{"name": " ", "hourlyWageYen": 1200}""", "name");
		assertThat(count("jobs")).isZero();
	}

	@Test
	void deletingAJobRemovesItUnlessItHasShiftsThenArchivesIt() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String bookshop = createJob(alice, "Bookshop", 1100);
		createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");

		assertThat(alice.delete("/api/jobs/" + cafe)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(alice.delete("/api/jobs/" + bookshop)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(alice.get("/api/jobs")).bodyJson().isLenientlyEqualTo("[]");
		assertThat(alice.get("/api/jobs?includeArchived=true")).bodyJson().isLenientlyEqualTo("""
				[{"id": "%s", "name": "Cafe", "archived": true}]""".formatted(cafe));
		assertThat(count("shifts")).isEqualTo(1);
	}

	@Test
	void anArchivedJobCanBeRestored() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T12:00");
		assertThat(alice.delete("/api/jobs/" + cafe)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(alice.post("/api/jobs/" + cafe + "/restore", "")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": "%s", "name": "Cafe", "archived": false}""".formatted(cafe));
		assertThat(alice.get("/api/jobs")).bodyJson().isLenientlyEqualTo("""
				[{"id": "%s", "archived": false}]""".formatted(cafe));
	}

	@Test
	void restoringAJobThatIsNotArchivedChangesNothing() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);

		assertThat(alice.post("/api/jobs/" + cafe + "/restore", "")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": "%s", "archived": false}""".formatted(cafe));
		assertThat(alice.post("/api/jobs/00000000-0000-0000-0000-000000000000/restore", ""))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	private static void assertInvalid(ApiClient client, String body, String field) {
		assertThat(client.post("/api/jobs", body)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactly(field);
	}

}
