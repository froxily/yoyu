package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import jakarta.servlet.http.Cookie;

class SessionVersionApiTest extends ApiTestSupport {

	@Test
	void aSessionStampedBeforeItsAccountsCredentialsChangedIsRejected() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.get("/api/auth/me")).hasStatusOk();

		jdbc.sql("UPDATE users SET session_version = session_version + 1").update();

		assertThat(alice.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(alice.get("/api/jobs")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("spring_session")).isZero();
		assertThat(new ApiClient(mvc).post("/api/auth/login", credentials("alice@example.com", PASSWORD)))
			.hasStatusOk();
	}

	@Test
	void anEncodedApiPathCannotSkipTheVersionCheck() throws Exception {
		ApiClient alice = signedUp("alice@example.com");
		Cookie session = alice.cookie(ApiClient.SESSION_COOKIE);
		jdbc.sql("UPDATE users SET session_version = session_version + 1").update();

		for (String path : new String[] { "/%61pi/auth/me", "/api/auth/%6De", "/api/%61uth/me" }) {
			MvcTestResult result = mvc.get().uri(java.net.URI.create(path)).cookie(session).exchange();
			assertThat(result.getResponse().getContentAsString()).as(path).doesNotContain("alice@example.com");
		}
	}

	@Test
	void encodedApiPathsStillRequireSignIn() throws Exception {
		ApiClient alice = signedUp("alice@example.com");
		createJob(alice, "Secret cafe", 1200);

		for (String path : new String[] { "/%61pi/jobs", "/api/%6Aobs", "/%61pi/auth/me" }) {
			MvcTestResult result = mvc.get().uri(java.net.URI.create(path)).exchange();
			assertThat(result.getResponse().getContentAsString()).as(path)
				.doesNotContain("Secret cafe")
				.doesNotContain("alice@example.com");
		}
	}

	@Test
	void passwordResetBumpsTheVersionSoEvenAnUnsweptSessionStopsWorking() {
		ApiClient alice = signedUp("alice@example.com");
		Cookie session = alice.cookie(ApiClient.SESSION_COOKIE);
		ApiClient resetter = new ApiClient(mvc);
		mail.clear();
		String verificationId = ApiClient.read(resetter.post("/api/auth/password/forgot", """
				{"identifier": "alice"}"""), "$.verificationId");

		assertThat(resetter.post("/api/auth/password/reset", """
				{"verificationId": "%s", "code": "%s", "password": "a brand new passphrase"}"""
			.formatted(verificationId, mail.lastCodeFor("alice@example.com")))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(jdbc.sql("SELECT session_version FROM users").query(Long.class).single()).isEqualTo(1);
		assertThat(mvc.get().uri("/api/auth/me").cookie(session)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void changingTheEmailKeepsTheCurrentSessionValidWithTheNewVersion() {
		ApiClient alice = signedUp("alice@example.com");
		ApiClient laptop = new ApiClient(mvc);
		assertThat(laptop.post("/api/auth/login", credentials("alice@example.com", PASSWORD))).hasStatusOk();
		String verificationId = ApiClient.read(alice.post("/api/account/email", """
				{"email": "alice.new@example.com"}"""), "$.verificationId");

		assertThat(alice.post("/api/account/email/verify",
				code(verificationId, mail.lastCodeFor("alice.new@example.com")))).hasStatusOk();

		assertThat(alice.get("/api/auth/me")).hasStatusOk()
			.bodyJson()
			.satisfies(json -> {
				assertThat(json).extractingPath("$.email").isEqualTo("alice.new@example.com");
				assertThat(json).extractingPath("$.recentlyAuthenticated").isEqualTo(true);
			});
		assertThat(laptop.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void parallelCodeChecksDoNotExhaustTheConnectionPool() throws Exception {
		ApiClient client = new ApiClient(mvc);
		assertThat(client.get("/api/health")).hasStatusOk();
		Cookie csrf = client.cookie(ApiClient.CSRF_COOKIE);
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			ids.add(startRegistration(new ApiClient(mvc), "user" + i + "@example.com", "user_" + i));
		}
		ExecutorService pool = Executors.newFixedThreadPool(12);
		try {
			List<Future<MvcTestResult>> results = new ArrayList<>();
			long started = System.nanoTime();
			for (String id : ids) {
				results.add(pool.submit(() -> mvc.post()
					.uri("/api/auth/register/verify")
					.cookie(csrf)
					.header(ApiClient.CSRF_HEADER, csrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content(code(id, "000000"))
					.exchange()));
			}
			for (Future<MvcTestResult> result : results) {
				assertThat(result.get(15, TimeUnit.SECONDS)).hasStatus(HttpStatus.BAD_REQUEST);
			}
			assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.sql("SELECT sum(attempts) FROM email_codes").query(Long.class).single()).isEqualTo(12);
		assertThat(ids).allSatisfy(id -> assertThat(UUID.fromString(id)).isNotNull());
	}

}
