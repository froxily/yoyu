package com.shift28.web;

import static com.shift28.web.LoginApiTest.login;
import static com.shift28.web.SocialSignInApiTest.callback;
import static com.shift28.web.SocialSignInApiTest.identity;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PasswordSetupApiTest extends ApiTestSupport {

	private static final String NEW_PASSWORD = "another strong passphrase";

	@Test
	void aGoogleOnlyAccountSetsAPasswordAfterAFreshSignInAndThenSignsInWithIt() {
		ApiClient client = social("google", "g-pass", "sam@example.com", clock.instant(), "sam_lee");

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatusOk().bodyJson()
			.extractingPath("$.hasPassword").isEqualTo(true);

		for (String identifier : new String[] { "sam_lee", "sam@example.com" }) {
			ApiClient device = new ApiClient(mvc);
			assertThat(device.post("/api/auth/login", login(identifier, NEW_PASSWORD))).hasStatusOk();
		}
		assertThat(client.get("/api/auth/me")).hasStatusOk();
		assertThat(mail.sentTo("sam@example.com")).singleElement()
			.satisfies(message -> assertThat(message.subject()).contains("A password was added"));
	}

	@Test
	void aSocialSignUpWithoutAFreshProviderSignInMustConfirmItIsYouFirst() {
		ApiClient client = social("google", "g-stale", "sam@example.com", null, "sam_lee");
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.recentlyAuthenticated")
			.isEqualTo(false);

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.reauthenticate").isEqualTo(true);
		assertNoPassword();
	}

	@Test
	void staleProofIsRejected() {
		ApiClient client = social("google", "g-aged", "sam@example.com", clock.instant(), "sam_lee");
		clock.set(clock.instant().plusSeconds(660));

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatus(HttpStatus.FORBIDDEN);
		assertNoPassword();
	}

	@Test
	void aCodeToTheVerifiedEmailConfirmsItIsYou() {
		ApiClient client = social("google", "g-code", "sam@example.com", null, "sam_lee");
		String id = ApiClient.read(client.post("/api/auth/reauthenticate/email", ""), "$.verificationId");

		assertThat(client.post("/api/auth/reauthenticate/email/verify", code(id, mail.lastCodeFor("sam@example.com"))))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatusOk();
	}

	@Test
	void wrongOrExpiredCodesDoNotConfirmItIsYou() {
		ApiClient client = social("google", "g-wrong", "sam@example.com", null, "sam_lee");
		String id = ApiClient.read(client.post("/api/auth/reauthenticate/email", ""), "$.verificationId");
		String value = mail.lastCodeFor("sam@example.com");
		String wrong = value.equals("000000") ? "111111" : "000000";

		assertThat(client.post("/api/auth/reauthenticate/email/verify", code(id, wrong))).hasStatus(HttpStatus.BAD_REQUEST);
		clock.set(clock.instant().plusSeconds(601));
		assertThat(client.post("/api/auth/reauthenticate/email/verify", code(id, value))).hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatus(HttpStatus.FORBIDDEN);
		assertNoPassword();
	}

	@Test
	void aLineAccountWithoutAnEmailAddsAndConfirmsOneBeforeSettingAPassword() {
		ApiClient client = social("line", "l-pass", null, clock.instant(), "line_user");

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
		assertNoPassword();

		String id = ApiClient.read(client.post("/api/account/email", """
				{"email": "line@example.com"}"""), "$.verificationId");
		assertThat(client.post("/api/account/email/verify", code(id, mail.lastCodeFor("line@example.com")))).hasStatusOk();

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatusOk();
		assertThat(new ApiClient(mvc).post("/api/auth/login", login("line_user", NEW_PASSWORD))).hasStatusOk();
	}

	@Test
	void changingAPasswordSignsOutOtherDevicesAndKeepsThisOne() {
		ApiClient client = signedUp("alice@example.com");
		ApiClient other = new ApiClient(mvc);
		assertThat(other.post("/api/auth/login", login("alice", PASSWORD))).hasStatusOk();
		mail.clear();

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatusOk();

		assertThat(client.get("/api/auth/me")).hasStatusOk();
		assertThat(other.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(new ApiClient(mvc).post("/api/auth/login", login("alice", PASSWORD))).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(new ApiClient(mvc).post("/api/auth/login", login("alice", NEW_PASSWORD))).hasStatusOk();
		assertThat(mail.sentTo("alice@example.com")).singleElement()
			.satisfies(message -> assertThat(message.subject()).contains("Your password was changed"));
	}

	@Test
	void weakPasswordsAreRejectedAndChangeNothing() {
		ApiClient client = social("google", "g-weak", "sam@example.com", clock.instant(), "sam_lee");

		for (String weak : new String[] { "short", "1234567890", "SAM@example.com" }) {
			assertThat(client.put("/api/account/password", password(weak))).as(weak).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
		}
		assertNoPassword();
	}

	@Test
	void signedOutAndCrossSiteRequestsCannotSetAPassword() {
		ApiClient client = social("google", "g-csrf", "sam@example.com", clock.instant(), "sam_lee");

		assertThat(new ApiClient(mvc).put("/api/account/password", password(NEW_PASSWORD)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult withoutToken = mvc.put().uri("/api/account/password")
			.cookie(client.cookie(ApiClient.SESSION_COOKIE))
			.contentType(MediaType.APPLICATION_JSON)
			.content(password(NEW_PASSWORD))
			.exchange();
		assertThat(withoutToken).hasStatus(HttpStatus.FORBIDDEN);
		assertNoPassword();
	}

	@Test
	void aPasswordMakesTheLastProviderUnlinkable() {
		ApiClient client = social("google", "g-only", "sam@example.com", clock.instant(), "sam_lee");
		assertThat(client.delete("/api/account/identities/google")).hasStatus(HttpStatus.CONFLICT);

		assertThat(client.put("/api/account/password", password(NEW_PASSWORD))).hasStatusOk();

		assertThat(client.delete("/api/account/identities/google")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(new ApiClient(mvc).post("/api/auth/login", login("sam_lee", NEW_PASSWORD))).hasStatusOk();
	}

	private ApiClient social(String provider, String subject, String email, Instant authTime, String username) {
		ApiClient client = new ApiClient(mvc);
		String identity = identity(subject, email, email == null ? null : true, authTime,
				provider.equals("line") ? new String[] { "pwd" } : new String[0]);
		assertThat(callback(client, provider, identity)).isEqualTo("/welcome");
		assertThat(client.post("/api/auth/social/complete", """
				{"fullName": "Social User", "username": "%s"}""".formatted(username))).hasStatus(HttpStatus.CREATED);
		mail.clear();
		return client;
	}

	private void assertNoPassword() {
		assertThat(jdbc.sql("SELECT count(*) FROM users WHERE password_hash IS NOT NULL").query(Integer.class).single())
			.isZero();
	}

	private static String password(String value) {
		return """
				{"password": "%s"}""".formatted(value);
	}

}
