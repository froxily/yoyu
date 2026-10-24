package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountApiTest extends ApiTestSupport {

	@Test
	void deletingTheAccountRemovesAllItsDataSignsOutAndLeavesOtherUsersAlone() {
		ApiClient alice = signedUp("alice@example.com");
		String aliceId = ApiClient.read(alice.get("/api/auth/me"), "$.id");
		String aliceJob = createJob(alice, "Cafe", 1200);
		createShift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T14:00");
		createdId(alice.post("/api/vacations", """
				{"startDate": "2026-12-21", "endDate": "2027-01-06"}"""));
		ApiClient bob = signedUp("bob@example.com");
		String bobJob = createJob(bob, "Bar", 1300);
		createShift(bob, bobJob, "2026-11-02T10:00", "2026-11-02T14:00");

		assertThat(alice.delete("/api/account")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(alice.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		for (String table : new String[] { "jobs", "shifts", "vacation_periods" }) {
			assertThat(rowsOf(table, "user_id", aliceId)).as(table).isZero();
		}
		assertThat(rowsOf("users", "id", aliceId)).isZero();
		assertThat(count("users")).isEqualTo(1);
		assertThat(count("jobs")).isEqualTo(1);
		assertThat(count("shifts")).isEqualTo(1);
		assertThat(new ApiClient(mvc).post("/api/auth/login", credentials("alice@example.com", PASSWORD)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(bob.get("/api/jobs")).hasStatusOk().bodyJson().extractingPath("$[*].id").asArray().containsExactly(bobJob);
	}

	@Test
	void deletingTheAccountRequiresSignIn() {
		assertThat(new ApiClient(mvc).delete("/api/account")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private long rowsOf(String table, String column, String userId) {
		return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = ?::uuid")
			.param(userId)
			.query(Long.class)
			.single();
	}

}
