package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import jakarta.servlet.http.Cookie;

class SessionApiTest extends ApiTestSupport {

	private static final int THIRTY_DAYS = (int) Duration.ofDays(30).toSeconds();

	@Test
	void signInStoresTheSessionInPostgresForThirtyDays() {
		ApiClient alice = signedUp("alice@example.com");
		String aliceId = ApiClient.read(alice.get("/api/auth/me"), "$.id");
		Cookie session = alice.cookie(ApiClient.SESSION_COOKIE);

		assertThat(session.getMaxAge()).isEqualTo(THIRTY_DAYS);
		assertThat(session.isHttpOnly()).isTrue();
		assertThat(session.getAttribute("SameSite")).isEqualToIgnoringCase("Lax");
		assertThat(jdbc.sql("SELECT max_inactive_interval FROM spring_session WHERE principal_name = ?")
			.param(aliceId)
			.query(Integer.class)
			.single()).isEqualTo(THIRTY_DAYS);
	}

	@Test
	void theSessionIsReadFromTheDatabaseOnEveryRequest() {
		ApiClient alice = signedUp("alice@example.com");
		Cookie session = alice.cookie(ApiClient.SESSION_COOKIE);

		assertThat(mvc.get().uri("/api/auth/me").cookie(session)).hasStatusOk();

		jdbc.sql("DELETE FROM spring_session").update();

		assertThat(mvc.get().uri("/api/auth/me").cookie(session)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void everySignedInRequestRenewsTheSessionCookieForThirtyDays() {
		ApiClient alice = signedUp("alice@example.com");
		Cookie session = alice.cookie(ApiClient.SESSION_COOKIE);

		MvcTestResult me = mvc.get().uri("/api/auth/me").cookie(session).exchange();

		assertThat(me).hasStatusOk();
		Cookie renewed = me.getResponse().getCookie(ApiClient.SESSION_COOKIE);
		assertThat(renewed).isNotNull();
		assertThat(renewed.getValue()).isEqualTo(session.getValue());
		assertThat(renewed.getMaxAge()).isEqualTo(THIRTY_DAYS);
		assertThat(renewed.isHttpOnly()).isTrue();
		assertThat(renewed.getAttribute("SameSite")).isEqualToIgnoringCase("Lax");
	}

	@Test
	void theHealthCheckNeitherRenewsNorReadsTheSession() {
		ApiClient alice = signedUp("alice@example.com");
		Cookie session = alice.cookie(ApiClient.SESSION_COOKIE);
		long lastAccess = lastAccessTime();

		MvcTestResult health = mvc.get().uri("/api/health").cookie(session).exchange();

		assertThat(health).hasStatusOk();
		assertThat(health.getResponse().getCookie(ApiClient.SESSION_COOKIE)).isNull();
		assertThat(lastAccessTime()).isEqualTo(lastAccess);
	}

	@Test
	void aSessionUsedWithinThirtyDaysStaysSignedInAndSlidesForward() {
		ApiClient alice = signedUp("alice@example.com");
		ageSessions(Duration.ofDays(29));

		assertThat(alice.get("/api/auth/me")).hasStatusOk();

		long expiry = jdbc.sql("SELECT expiry_time FROM spring_session").query(Long.class).single();
		assertThat(Instant.ofEpochMilli(expiry)).isAfter(Instant.now().plus(Duration.ofDays(30)).minusSeconds(60));
	}

	@Test
	void aSessionUnusedForMoreThanThirtyDaysExpires() {
		ApiClient alice = signedUp("alice@example.com");
		ageSessions(Duration.ofDays(31));

		assertThat(alice.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private void ageSessions(Duration age) {
		jdbc.sql("""
				UPDATE spring_session
				SET last_access_time = last_access_time - ?, expiry_time = expiry_time - ?""")
			.param(age.toMillis())
			.param(age.toMillis())
			.update();
	}

	private long lastAccessTime() {
		return jdbc.sql("SELECT last_access_time FROM spring_session").query(Long.class).single();
	}

	@Test
	void deletingTheAccountEndsItsSessionsOnEveryDevice() {
		ApiClient phone = signedUp("alice@example.com");
		ApiClient laptop = new ApiClient(mvc);
		assertThat(laptop.post("/api/auth/login", credentials("alice@example.com", PASSWORD))).hasStatusOk();
		ApiClient bob = signedUp("bob@example.com");

		assertThat(phone.delete("/api/account")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(laptop.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("spring_session")).isEqualTo(1);
		assertThat(bob.get("/api/auth/me")).hasStatusOk();
	}

}
