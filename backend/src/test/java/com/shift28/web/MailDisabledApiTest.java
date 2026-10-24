package com.shift28.web;

import static com.shift28.web.LoginApiTest.login;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "mail.enabled=false")
class MailDisabledApiTest extends ApiTestSupport {

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void providersReportEmailAsUnavailable() {
		assertThat(new ApiClient(mvc).get("/api/auth/providers")).hasStatusOk().bodyJson()
			.extractingPath("$.email").isEqualTo(false);
	}

	@Test
	void registrationIsUnavailable() {
		assertThat(new ApiClient(mvc).post("/api/auth/register", registration("alice@example.com", "alice", PASSWORD)))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(count("users")).isZero();
		assertThat(mail.sent()).isEmpty();
	}

	@Test
	void passwordResetRequestsAreUnavailable() {
		assertThat(new ApiClient(mvc).post("/api/auth/password/forgot", """
				{"identifier": "alice@example.com"}""")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(mail.sent()).isEmpty();
	}

	@Test
	void emailChangesAreUnavailable() {
		ApiClient client = legacySignedIn();

		assertThat(client.post("/api/account/email", """
				{"email": "new@example.com"}""")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email")
			.isEqualTo("legacy@example.com");
		assertThat(mail.sent()).isEmpty();
	}

	@Test
	void legacyUnverifiedAccountSignsInWithoutEmail() {
		ApiClient client = legacySignedIn();

		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.emailVerified").isEqualTo(false);
			assertThat(json).extractingPath("$.username").isNull();
		});
		assertThat(storedSessions(client.cookie(ApiClient.SESSION_COOKIE))).isOne();
		assertThat(count("email_codes")).isZero();
		assertThat(mail.sent()).isEmpty();
	}

	private ApiClient legacySignedIn() {
		jdbc.sql("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)")
			.params(UUID.randomUUID(), "legacy@example.com", passwordEncoder.encode(PASSWORD)).update();
		ApiClient client = new ApiClient(mvc);
		assertThat(client.post("/api/auth/login", login("legacy@example.com", PASSWORD))).hasStatusOk();
		return client;
	}

}
