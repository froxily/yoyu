package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SocialSignInApiTest extends ApiTestSupport {

	static String identity(String subject, String email, Boolean emailVerified, Instant authTime, String... amr) {
		return """
				{"subject": "%s", "email": %s, "emailVerified": %s, "name": "Social User", "authTime": %s, "amr": %s}"""
			.formatted(subject, email == null ? "null" : "\"" + email + "\"", emailVerified,
					authTime == null ? "null" : "\"" + authTime + "\"",
					amr.length == 0 ? "null" : "[\"" + String.join("\",\"", amr) + "\"]");
	}

	static String callback(ApiClient client, String provider, String identity) {
		MvcTestResult result = client.post(FakeOAuthCallbackController.PATH + provider, identity);
		assertThat(result).hasStatusOk();
		return ApiClient.read(result, "$.redirect");
	}

	@Test
	void aNewGoogleIdentityChoosesAUsernameThenSignsInWithTheVerifiedEmail() {
		ApiClient client = new ApiClient(mvc);

		assertThat(callback(client, "google", identity("g-1", "sam@example.com", true, null))).isEqualTo("/welcome");
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.get("/api/auth/social/pending")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo("sam@example.com");

		assertThat(client.post("/api/auth/social/complete", """
				{"fullName": "Sam Lee", "username": "Sam_Lee"}""")).hasStatus(HttpStatus.CREATED);

		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.username").isEqualTo("sam_lee");
			assertThat(json).extractingPath("$.email").isEqualTo("sam@example.com");
			assertThat(json).extractingPath("$.emailVerified").isEqualTo(true);
			assertThat(json).extractingPath("$.hasPassword").isEqualTo(false);
			assertThat(json).extractingPath("$.identities[0].provider").isEqualTo("google");
		});
		assertThat(client.get("/api/auth/social/pending")).hasStatus(HttpStatus.NOT_FOUND);

		ApiClient again = new ApiClient(mvc);
		assertThat(callback(again, "google", identity("g-1", "sam@example.com", true, null))).isEqualTo("/");
		assertThat(again.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.username").isEqualTo("sam_lee");
		assertThat(count("users")).isOne();
	}

	@Test
	void aVerifiedGoogleEmailThatBelongsToAnAccountIsNeverMerged() {
		signedUp("alice@example.com");
		ApiClient attacker = new ApiClient(mvc);

		assertThat(callback(attacker, "google", identity("g-2", "ALICE@example.com", true, null)))
			.isEqualTo("/login?error=link-required");

		assertThat(attacker.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(attacker.get("/api/auth/social/pending")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(count("user_identities")).isZero();
	}

}
