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

class AccountSecurityApiTest extends ApiTestSupport {

	@Test
	void recentAuthenticationBecomesFalseAfterElevenMinutes() {
		ApiClient client = signedUp("alice@example.com");
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson()
			.extractingPath("$.recentlyAuthenticated").isEqualTo(true);
		ageSession();

		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson()
			.extractingPath("$.recentlyAuthenticated").isEqualTo(false);
	}

	@Test
	void deletionRequiresRecentAuthenticationAndPreservesTheAccount() {
		ApiClient client = signedUp("alice@example.com");
		ageSession();

		assertThat(client.delete("/api/account")).hasStatus(HttpStatus.FORBIDDEN).bodyJson()
			.extractingPath("$.reauthenticate").isEqualTo(true);
		assertThat(count("users")).isOne();
		assertThat(client.get("/api/auth/me")).hasStatusOk();
	}

	@Test
	void wrongReauthenticationPasswordKeepsTheSessionAndCorrectPasswordPermitsDeletion() {
		ApiClient client = signedUp("alice@example.com");
		ageSession();

		assertThat(client.post("/api/auth/reauthenticate", password("wrong password")))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson()
			.extractingPath("$.recentlyAuthenticated").isEqualTo(false);
		assertThat(client.post("/api/auth/reauthenticate", password(PASSWORD))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client.delete("/api/account")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("users")).isZero();
	}

	@Test
	void emailReauthenticationPermitsASensitiveAction() {
		ApiClient client = signedUp("alice@example.com");
		ageSession();
		mail.clear();

		MvcTestResult started = client.post("/api/auth/reauthenticate/email", "");

		assertThat(started).hasStatus(HttpStatus.ACCEPTED);
		assertThat(mail.sentTo("alice@example.com")).hasSize(1);
		assertThat(client.post("/api/auth/reauthenticate/email/verify",
				code(ApiClient.read(started, "$.verificationId"), mail.lastCodeFor("alice@example.com"))))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson()
			.extractingPath("$.recentlyAuthenticated").isEqualTo(true);
		assertThat(client.delete("/api/account")).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void profileUpdatesFullNameAndUsername() {
		ApiClient client = signedUp("alice@example.com");

		assertThat(client.put("/api/account/profile", profile("Alice Smith", "Alice_Smith"))).hasStatusOk()
			.bodyJson().satisfies(json -> {
				assertThat(json).extractingPath("$.fullName").isEqualTo("Alice Smith");
				assertThat(json).extractingPath("$.username").isEqualTo("alice_smith");
			});
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.fullName").isEqualTo("Alice Smith");
			assertThat(json).extractingPath("$.username").isEqualTo("alice_smith");
		});
	}

	@Test
	void profileRejectsATakenUsernameIgnoringCase() {
		ApiClient client = signedUp("alice@example.com");
		signedUp("bob@example.com");

		assertThat(client.put("/api/account/profile", profile("Alice Smith", "BOB")))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.errors[0].field").isEqualTo("username");
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.username").isEqualTo("alice");
	}

	@Test
	void profileRejectsAnInvalidUsername() {
		ApiClient client = signedUp("alice@example.com");

		assertThat(client.put("/api/account/profile", profile("Alice Smith", "has space")))
			.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].field").isEqualTo("username");
	}

	@Test
	void emailChangeRequiresRecentAuthentication() {
		ApiClient client = signedUp("alice@example.com");
		ageSession();
		mail.clear();

		assertThat(client.post("/api/account/email", email("new@example.com"))).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.reauthenticate").isEqualTo(true);
		assertThat(mail.sent()).isEmpty();
	}

	@Test
	void emailChangeSendsTheCodeOnlyToTheNewAddress() {
		ApiClient client = signedUp("alice@example.com");
		mail.clear();

		startChange(client, "new@example.com");

		assertThat(mail.sentTo("new@example.com")).singleElement().satisfies(message ->
			assertThat(message.body()).containsPattern("\\b\\d{6}\\b"));
		assertThat(mail.sentTo("alice@example.com")).isEmpty();
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email")
			.isEqualTo("alice@example.com");
	}

	@Test
	void takenEmailHasTheSameAcceptedResponseButOnlySendsANotice() {
		ApiClient client = signedUp("alice@example.com");
		signedUp("bob@example.com");
		mail.clear();
		MvcTestResult free = client.post("/api/account/email", email("new@example.com"));
		MvcTestResult taken = client.post("/api/account/email", email("bob@example.com"));

		assertThat(free).hasStatus(HttpStatus.ACCEPTED);
		assertThat(taken).hasStatus(HttpStatus.ACCEPTED);
		Map<String, Object> freeBody = ApiClient.read(free, "$");
		Map<String, Object> takenBody = ApiClient.read(taken, "$");
		assertThat(takenBody).containsOnlyKeys("verificationId", "resendAfterSeconds");
		assertThat(UUID.fromString((String) takenBody.get("verificationId"))).isNotNull();
		freeBody.remove("verificationId");
		takenBody.remove("verificationId");
		assertThat(takenBody).isEqualTo(freeBody);
		assertThat(mail.sentTo("bob@example.com")).singleElement().satisfies(message ->
			assertThat(message.body()).contains("nothing was changed").doesNotContainPattern("\\d{6}"));
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email")
			.isEqualTo("alice@example.com");
		assertThat(jdbc.sql("SELECT email FROM users ORDER BY email").query(String.class).list())
			.containsExactly("alice@example.com", "bob@example.com");
	}

	@Test
	void verifiedEmailChangeNotifiesTheOldAddressAndRevokesOnlyOtherSessions() {
		ApiClient client = signedUp("alice@example.com");
		ApiClient other = new ApiClient(mvc);
		assertThat(other.post("/api/auth/login", login("alice", PASSWORD))).hasStatusOk();
		String userId = ApiClient.read(client.get("/api/auth/me"), "$.id");
		mail.clear();
		String id = startChange(client, "new@example.com");

		assertThat(client.post("/api/account/email/verify", code(id, mail.lastCodeFor("new@example.com"))))
			.hasStatusOk().bodyJson().satisfies(json -> {
				assertThat(json).extractingPath("$.email").isEqualTo("new@example.com");
				assertThat(json).extractingPath("$.emailVerified").isEqualTo(true);
			});
		assertThat(mail.sentTo("alice@example.com")).singleElement().satisfies(message ->
			assertThat(message.body()).contains("was changed").doesNotContainPattern("\\d{6}"));
		assertThat(other.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email")
			.isEqualTo("new@example.com");
		assertThat(storedSessions(client.cookie(ApiClient.SESSION_COOKIE))).isOne();
		assertThat(jdbc.sql("SELECT count(*) FROM spring_session WHERE principal_name = ?").param(userId)
			.query(Long.class).single()).isOne();
		assertThat(jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?")
			.param(UUID.fromString(userId)).query(Boolean.class).single()).isTrue();
	}

	@Test
	void verificationRejectsAnEmailChangedWhileTheCodeWasPending() {
		ApiClient client = signedUp("alice@example.com");
		String id = startChange(client, "new@example.com");
		jdbc.sql("UPDATE users SET email = 'concurrent@example.com' WHERE email = 'alice@example.com'").update();

		assertThat(client.post("/api/account/email/verify", code(id, mail.lastCodeFor("new@example.com"))))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email")
			.isEqualTo("concurrent@example.com");
	}

	@Test
	void onlyTheFirstUserCanVerifyAChangeToTheSameFreeAddress() {
		ApiClient alice = signedUp("alice@example.com");
		ApiClient bob = signedUp("bob@example.com");
		String first = startChange(alice, "shared@example.com");
		String firstCode = mail.lastCodeFor("shared@example.com");
		String second = startChange(bob, "shared@example.com");
		String secondCode = mail.lastCodeFor("shared@example.com");

		assertThat(alice.post("/api/account/email/verify", code(first, firstCode))).hasStatusOk();
		assertThat(bob.post("/api/account/email/verify", code(second, secondCode))).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.errors[0].field").isEqualTo("email");
		assertThat(bob.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email").isEqualTo("bob@example.com");
	}

	@Test
	void deletionRemovesTheUserIdentitiesAndPendingCodes() {
		ApiClient client = signedUp("alice@example.com");
		String userId = ApiClient.read(client.get("/api/auth/me"), "$.id");
		assertThat(client.post("/api/account/identities/google/link", "")).hasStatusOk();
		assertThat(callback(client, "google", identity("delete-google", null, null, null))).isEqualTo("/settings?linked=google");
		startChange(client, "new@example.com");
		assertThat(count("user_identities")).isOne();
		assertThat(count("email_codes")).isOne();

		assertThat(client.delete("/api/account")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(jdbc.sql("SELECT count(*) FROM users WHERE id = ?").param(UUID.fromString(userId))
			.query(Long.class).single()).isZero();
		for (String table : new String[] { "user_identities", "email_codes" }) {
			assertThat(jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = ?")
				.param(UUID.fromString(userId)).query(Long.class).single()).as(table).isZero();
		}
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private void ageSession() {
		clock.set(clock.instant().plusSeconds(660));
	}

	@Test
	void aConfirmationCodeSentToThePreviousEmailStopsWorkingOnceTheEmailChanges() {
		ApiClient client = signedUp("alice@example.com");
		String staleId = ApiClient.read(client.post("/api/auth/reauthenticate/email", ""), "$.verificationId");
		String staleCode = mail.lastCodeFor("alice@example.com");
		String change = startChange(client, "alice.new@example.com");
		assertThat(client.post("/api/account/email/verify", code(change, mail.lastCodeFor("alice.new@example.com"))))
			.hasStatusOk();

		assertThat(client.post("/api/auth/reauthenticate/email/verify", code(staleId, staleCode)))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	private String startChange(ApiClient client, String address) {
		MvcTestResult result = client.post("/api/account/email", email(address));
		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		return ApiClient.read(result, "$.verificationId");
	}

	private static String email(String address) {
		return """
				{"email": "%s"}""".formatted(address);
	}

	private static String profile(String fullName, String username) {
		return """
				{"fullName": "%s", "username": "%s"}""".formatted(fullName, username);
	}

	private static String password(String value) {
		return """
				{"password": "%s"}""".formatted(value);
	}

}
