package com.shift28.web;

import static com.shift28.web.LoginApiTest.login;
import static com.shift28.web.SocialSignInApiTest.callback;
import static com.shift28.web.SocialSignInApiTest.identity;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PasswordResetApiTest extends ApiTestSupport {

	private static final String NEW_PASSWORD = "another strong passphrase";

	@Test
	void forgotResponsesHaveTheSameKeysForUnknownSocialEmailAndUsernameIdentifiers() {
		signedUp("alice@example.com");
		ApiClient social = new ApiClient(mvc);
		assertThat(callback(social, "google", identity("social-reset", "social@example.com", true, null)))
			.isEqualTo("/welcome");
		assertThat(social.post("/api/auth/social/complete", """
				{"fullName": "Social User", "username": "social_user"}""")).hasStatus(HttpStatus.CREATED);
		mail.clear();
		ApiClient client = new ApiClient(mvc);

		for (String identifier : new String[] { "unknown@example.com", "social@example.com", "alice@example.com", "ALICE" }) {
			MvcTestResult result = client.post("/api/auth/password/forgot", forgot(identifier));
			assertThat(result).hasStatus(HttpStatus.ACCEPTED);
			Map<String, Object> body = ApiClient.read(result, "$");
			assertThat(body).containsOnlyKeys("verificationId", "resendAfterSeconds")
				.containsEntry("resendAfterSeconds", 60);
			assertThat(UUID.fromString((String) body.get("verificationId"))).isNotNull();
		}
		assertThat(mail.sentTo("unknown@example.com")).isEmpty();
		assertThat(mail.sentTo("social@example.com")).singleElement().satisfies(message ->
			assertThat(message.body()).contains("Google or LINE").doesNotContainPattern("\\d{6}"));
		assertThat(mail.sentTo("alice@example.com")).hasSize(2).allSatisfy(message ->
			assertThat(message.body()).containsPattern("\\b\\d{6}\\b"));
	}

	@Test
	void resetChangesThePasswordAndRevokesEveryExistingSession() {
		ApiClient first = signedUp("alice@example.com");
		String userId = ApiClient.read(first.get("/api/auth/me"), "$.id");
		ApiClient second = new ApiClient(mvc);
		assertThat(second.post("/api/auth/login", login("alice", PASSWORD))).hasStatusOk();
		assertThat(storedSessions(first.cookie(ApiClient.SESSION_COOKIE))).isOne();
		assertThat(storedSessions(second.cookie(ApiClient.SESSION_COOKIE))).isOne();
		ApiClient reset = new ApiClient(mvc);
		String id = startReset(reset);

		assertThat(reset.post("/api/auth/password/reset", reset(id, mail.lastCodeFor("alice@example.com"), NEW_PASSWORD)))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(first.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(second.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(jdbc.sql("SELECT count(*) FROM spring_session WHERE principal_name = ?").param(userId)
			.query(Long.class).single()).isZero();
		assertThat(reset.post("/api/auth/login", login("alice", PASSWORD))).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(reset.post("/api/auth/login", login("alice", NEW_PASSWORD))).hasStatusOk();
	}

	@Test
	void resetAlsoVerifiesTheAccountEmail() {
		signedUp("alice@example.com");
		jdbc.sql("UPDATE users SET email_verified_at = NULL WHERE email = 'alice@example.com'").update();
		ApiClient client = new ApiClient(mvc);
		String id = startReset(client);

		assertThat(client.post("/api/auth/password/reset", reset(id, mail.lastCodeFor("alice@example.com"), NEW_PASSWORD)))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE email = 'alice@example.com'")
			.query(Boolean.class).single()).isTrue();
		assertThat(client.post("/api/auth/login", login("alice", NEW_PASSWORD))).hasStatusOk().bodyJson()
			.extractingPath("$.emailVerified").isEqualTo(true);
	}

	@Test
	void resetCodeIsSingleUse() {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);
		String id = startReset(client);
		String body = reset(id, mail.lastCodeFor("alice@example.com"), NEW_PASSWORD);

		assertThat(client.post("/api/auth/password/reset", body)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client.post("/api/auth/password/reset", body)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void commonPasswordIsRejectedWithoutConsumingTheCode() {
		assertInvalidPasswordLeavesCodeUsable("1234567890");
	}

	@Test
	void shortPasswordIsRejectedWithoutConsumingTheCode() {
		assertInvalidPasswordLeavesCodeUsable("short");
	}

	@Test
	void fourthForgotRequestIsLimitedAcrossEmailAndUsername() {
		signedUp("alice@example.com");
		mail.clear();
		ApiClient client = new ApiClient(mvc);
		for (String identifier : new String[] { "alice@example.com", "ALICE", "ALICE@EXAMPLE.COM" }) {
			assertThat(client.post("/api/auth/password/forgot", forgot(identifier))).hasStatus(HttpStatus.ACCEPTED);
		}

		MvcTestResult result = client.post("/api/auth/password/forgot", forgot("alice"));

		assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
		assertThat(mail.sentTo("alice@example.com")).hasSize(3);
	}

	@Test
	void forgotRequestsFromAnotherClientDoNotBlockTheOwnersReset() {
		signedUp("alice@example.com");
		mail.clear();
		ApiClient attacker = new ApiClient(mvc, "203.0.113.7");
		for (int attempt = 0; attempt < 3; attempt++) {
			assertThat(attacker.post("/api/auth/password/forgot", forgot("alice"))).hasStatus(HttpStatus.ACCEPTED);
		}
		assertThat(attacker.post("/api/auth/password/forgot", forgot("alice"))).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		ApiClient owner = new ApiClient(mvc);

		String id = startReset(owner);

		assertThat(owner.post("/api/auth/password/reset", reset(id, mail.lastCodeFor("alice@example.com"), NEW_PASSWORD)))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void tenForgotRequestsFromAnyClientsReachTheAccountLimit() {
		signedUp("alice@example.com");
		mail.clear();
		for (int request = 0; request < 10; request++) {
			ApiClient client = new ApiClient(mvc, "203.0.113." + (request / 3 + 1));
			assertThat(client.post("/api/auth/password/forgot", forgot("alice"))).hasStatus(HttpStatus.ACCEPTED);
		}

		MvcTestResult result = new ApiClient(mvc, "198.51.100.9").post("/api/auth/password/forgot", forgot("alice"));

		assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
		assertThat(mail.sentTo("alice@example.com")).hasSize(10);
	}

	private void assertInvalidPasswordLeavesCodeUsable(String password) {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);
		String id = startReset(client);
		String value = mail.lastCodeFor("alice@example.com");

		assertThat(client.post("/api/auth/password/reset", reset(id, value, password))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
		assertThat(client.post("/api/auth/password/reset", reset(id, value, NEW_PASSWORD))).hasStatus(HttpStatus.NO_CONTENT);
	}

	private String startReset(ApiClient client) {
		MvcTestResult result = client.post("/api/auth/password/forgot", forgot("alice@example.com"));
		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		return ApiClient.read(result, "$.verificationId");
	}

	private static String forgot(String identifier) {
		return """
				{"identifier": "%s"}""".formatted(identifier);
	}

	private static String reset(String id, String value, String password) {
		return """
				{"verificationId": "%s", "code": "%s", "password": "%s"}""".formatted(id, value, password);
	}

}
