package com.shift28.web;

import static com.shift28.web.SocialSignInApiTest.callback;
import static com.shift28.web.SocialSignInApiTest.identity;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SocialLinkingApiTest extends ApiTestSupport {

	@Test
	void lineWithoutEmailCreatesAnAccountWithoutEmailOrPassword() {
		ApiClient client = new ApiClient(mvc);

		assertThat(callback(client, "line", identity("line-new", null, null, null))).isEqualTo("/welcome");
		assertThat(client.get("/api/auth/social/pending")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.email").isNull();
			assertThat(json).extractingPath("$.suggestedEmail").isNull();
		});
		assertThat(client.post("/api/auth/social/complete", profile("line_user"))).hasStatus(HttpStatus.CREATED);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.email").isNull();
			assertThat(json).extractingPath("$.hasPassword").isEqualTo(false);
		});
	}

	@Test
	void lineEmailIsOnlySuggestedAndNeverStoredAsTrustedEmail() {
		ApiClient client = new ApiClient(mvc);

		assertThat(callback(client, "line", identity("line-email", "line@example.com", true, null))).isEqualTo("/welcome");
		assertThat(client.get("/api/auth/social/pending")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.email").isNull();
			assertThat(json).extractingPath("$.suggestedEmail").isEqualTo("line@example.com");
		});
		assertThat(client.post("/api/auth/social/complete", profile("line_user"))).hasStatus(HttpStatus.CREATED);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.email").isNull();
			assertThat(json).extractingPath("$.emailVerified").isEqualTo(false);
		});
		assertThat(jdbc.sql("SELECT email IS NULL FROM users").query(Boolean.class).single()).isTrue();
	}

	@Test
	void unverifiedGoogleEmailIsNotStored() {
		ApiClient client = new ApiClient(mvc);

		assertThat(callback(client, "google", identity("google-unverified", "google@example.com", false, null)))
			.isEqualTo("/welcome");
		assertThat(client.post("/api/auth/social/complete", profile("google_user"))).hasStatus(HttpStatus.CREATED);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email").isNull();
		assertThat(jdbc.sql("SELECT email IS NULL FROM users").query(Boolean.class).single()).isTrue();
	}

	@Test
	void signedInUserCanLinkANewGoogleIdentity() {
		ApiClient client = signedUp("alice@example.com");
		String userId = userId(client);

		assertThat(client.post("/api/account/identities/google/link", "")).hasStatusOk().bodyJson()
			.extractingPath("$.redirect").isEqualTo("/oauth2/authorization/google");
		assertThat(callback(client, "google", identity("google-link", null, null, null))).isEqualTo("/settings?linked=google");
		assertThat(jdbc.sql("SELECT user_id FROM user_identities WHERE provider = 'google' AND subject = 'google-link'")
			.query(UUID.class).single()).isEqualTo(UUID.fromString(userId));
		assertThat(userId(client)).isEqualTo(userId);
	}

	@Test
	void linkingRequiresRecentAuthentication() {
		ApiClient client = signedUp("alice@example.com");
		ageSession();

		assertThat(client.post("/api/account/identities/google/link", "")).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.reauthenticate").isEqualTo(true);
		assertThat(count("user_identities")).isZero();
	}

	@Test
	void identityOwnedByAnotherUserCannotBeLinked() {
		ApiClient owner = socialAccount("google", "owned-google", "owner");
		String ownerId = userId(owner);
		ApiClient client = signedUp("alice@example.com");
		String aliceId = userId(client);
		assertThat(client.post("/api/account/identities/google/link", "")).hasStatusOk();

		assertThat(callback(client, "google", identity("owned-google", null, null, null)))
			.isEqualTo("/settings?error=identity-in-use");
		assertThat(jdbc.sql("SELECT user_id FROM user_identities").query(UUID.class).list())
			.containsExactly(UUID.fromString(ownerId));
		assertThat(userId(client)).isEqualTo(aliceId);
	}

	@Test
	void aSecondIdentityFromTheSameProviderCannotBeLinked() {
		ApiClient client = signedUp("alice@example.com");
		link(client, "google", "first-google");
		assertThat(client.post("/api/account/identities/google/link", "")).hasStatusOk();

		assertThat(callback(client, "google", identity("second-google", null, null, null)))
			.isEqualTo("/settings?error=provider-already-linked");
		assertThat(jdbc.sql("SELECT subject FROM user_identities").query(String.class).list()).containsExactly("first-google");
	}

	@Test
	void linkedIdentitySignsInItsOwner() {
		ApiClient owner = signedUp("alice@example.com");
		String ownerId = userId(owner);
		link(owner, "google", "returning-google");
		signedUp("bob@example.com");
		ApiClient client = new ApiClient(mvc);

		assertThat(callback(client, "google", identity("returning-google", null, null, null))).isEqualTo("/");
		assertThat(userId(client)).isEqualTo(ownerId);
	}

	@Test
	void expiredLinkIntentIsIgnoredAndCallbackUsesNormalSignIn() {
		ApiClient owner = socialAccount("google", "existing-google", "owner");
		String ownerId = userId(owner);
		ApiClient client = signedUp("alice@example.com");
		assertThat(client.post("/api/account/identities/google/link", "")).hasStatusOk();
		ageSession();

		assertThat(callback(client, "google", identity("existing-google", null, null, null))).isEqualTo("/");
		assertThat(userId(client)).isEqualTo(ownerId);
		assertThat(count("user_identities")).isOne();
	}

	@Test
	void socialOnlyAccountCannotUnlinkItsOnlySignInMethod() {
		ApiClient client = socialAccount("google", "only-google", "social_user");

		assertThat(client.delete("/api/account/identities/google")).hasStatus(HttpStatus.CONFLICT);
		assertThat(count("user_identities")).isOne();
	}

	@Test
	void recentlyAuthenticatedPasswordAccountCanUnlinkAnIdentity() {
		ApiClient client = signedUp("alice@example.com");
		link(client, "google", "removable-google");
		ageSession();
		assertThat(client.post("/api/auth/reauthenticate", """
				{"password": "%s"}""".formatted(PASSWORD))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(client.delete("/api/account/identities/google")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("user_identities")).isZero();
		assertThat(client.get("/api/auth/me")).hasStatusOk();
	}

	@Test
	void unlinkingRequiresRecentAuthentication() {
		ApiClient client = signedUp("alice@example.com");
		link(client, "google", "retained-google");
		ageSession();

		assertThat(client.delete("/api/account/identities/google")).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.reauthenticate").isEqualTo(true);
		assertThat(count("user_identities")).isOne();
	}

	@Test
	void reauthenticationRequiresALinkedProvider() {
		ApiClient client = signedUp("alice@example.com");

		assertThat(client.post("/api/account/identities/line/reauthenticate", "")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void freshLinePasswordAuthenticationRefreshesProofWithoutChangingTheUser() {
		ApiClient client = staleLineAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "line", identity("linked-line", null, null, clock.instant(), "pwd")))
			.isEqualTo("/settings?reauthenticated=line");
		assertProofAndUser(client, true, id);
	}

	@Test
	void lineAutomaticLoginDoesNotRefreshProof() {
		assertLineReauthenticationRejected(clock.instant().plusSeconds(660), "lineautologin");
	}

	@Test
	void authenticationOlderThanFiveMinutesDoesNotRefreshProof() {
		assertLineReauthenticationRejected(clock.instant().plusSeconds(359), "pwd");
	}

	@Test
	void missingAuthenticationTimeDoesNotRefreshProof() {
		assertLineReauthenticationRejected(null, "pwd");
	}

	@Test
	void differentUnlinkedLineSubjectCannotReauthenticateOrChangeTheUser() {
		ApiClient client = staleLineAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "line", identity("unlinked-line", null, null, clock.instant(), "pwd")))
			.isEqualTo("/settings?error=identity-mismatch");
		assertProofAndUser(client, false, id);
		assertThat(count("user_identities")).isOne();
	}

	@Test
	void reauthenticationWithAnotherUsersIdentityNeverSwitchesTheSignedInUser() {
		socialAccount("line", "other-line", "other_user");
		ApiClient client = staleLineAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "line", identity("other-line", null, null, clock.instant(), "pwd")))
			.isEqualTo("/settings?error=identity-mismatch");
		assertProofAndUser(client, false, id);
	}

	@Test
	void normalGoogleSignInWithFreshAuthenticationIsRecentlyAuthenticated() {
		ApiClient owner = socialAccount("google", "fresh-google", "google_user");
		String id = userId(owner);
		ApiClient client = new ApiClient(mvc);

		assertThat(callback(client, "google", identity("fresh-google", null, null, clock.instant()))).isEqualTo("/");
		assertProofAndUser(client, true, id);
	}

	@Test
	void normalGoogleSignInWithoutAuthenticationTimeIsNotRecentlyAuthenticated() {
		ApiClient owner = socialAccount("google", "no-time-google", "google_user");
		String id = userId(owner);
		ApiClient client = new ApiClient(mvc);

		assertThat(callback(client, "google", identity("no-time-google", null, null, null))).isEqualTo("/");
		assertProofAndUser(client, false, id);
	}

	@Test
	void aGoogleReauthenticationWithoutAuthenticationTimeSaysGoogleCannotConfirmIt() {
		ApiClient client = staleGoogleAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/google/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "google", identity("stale-google", "gina@example.com", true, null)))
			.isEqualTo("/settings?error=reauth-google-no-time");
		assertProofAndUser(client, false, id);
	}

	@Test
	void googleAuthenticationMethodsWithoutAuthenticationTimeNeverConfirmIt() {
		ApiClient client = staleGoogleAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/google/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "google", identity("stale-google", "gina@example.com", true, null, "pwd", "mfa")))
			.isEqualTo("/settings?error=reauth-google-no-time");
		assertProofAndUser(client, false, id);
	}

	@Test
	void aGoogleReauthenticationWithAFreshAuthenticationTimeConfirmsIt() {
		ApiClient client = staleGoogleAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/google/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "google", identity("stale-google", "gina@example.com", true, clock.instant())))
			.isEqualTo("/settings?reauthenticated=google");
		assertProofAndUser(client, true, id);
	}

	@Test
	void aGoogleReauthenticationWithAnOldAuthenticationTimeIsNotFresh() {
		ApiClient client = staleGoogleAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/google/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "google",
				identity("stale-google", "gina@example.com", true, clock.instant().minusSeconds(301))))
			.isEqualTo("/settings?error=reauth-not-fresh");
		assertProofAndUser(client, false, id);
	}

	@Test
	void aGoogleOnlyAccountConfirmsWithACodeToItsGoogleEmailAndThenLinksLine() {
		ApiClient client = staleGoogleAccount();
		assertThat(client.post("/api/account/identities/line/link", "")).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.reauthenticate").isEqualTo(true);
		String verificationId = ApiClient.read(client.post("/api/auth/reauthenticate/email", ""), "$.verificationId");

		assertThat(client.post("/api/auth/reauthenticate/email/verify",
				code(verificationId, mail.lastCodeFor("gina@example.com")))).hasStatus(HttpStatus.NO_CONTENT);
		link(client, "line", "gina-line");

		assertThat(count("user_identities")).isEqualTo(2);
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.identities[1].provider")
			.isNotNull();
	}

	@Test
	void socialCompletionWithoutPendingSignUpIsNotFound() {
		assertThat(new ApiClient(mvc).post("/api/auth/social/complete", profile("social_user")))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(count("users")).isZero();
	}

	@Test
	void pendingSignUpExpiresAtTenMinutes() {
		ApiClient client = new ApiClient(mvc);
		assertThat(callback(client, "line", identity("expired-line", null, null, null))).isEqualTo("/welcome");
		clock.set(clock.instant().plusSeconds(600));

		assertThat(client.post("/api/auth/social/complete", profile("social_user"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(client.get("/api/auth/social/pending")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(count("users")).isZero();
	}

	@Test
	void socialCompletionRejectsATakenUsername() {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);
		assertThat(callback(client, "line", identity("taken-line", null, null, null))).isEqualTo("/welcome");

		assertThat(client.post("/api/auth/social/complete", profile("ALICE"))).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.errors[0].field").isEqualTo("username");
		assertThat(count("users")).isOne();
		assertThat(count("user_identities")).isZero();
	}

	private ApiClient socialAccount(String provider, String subject, String username) {
		ApiClient client = new ApiClient(mvc);
		assertThat(callback(client, provider, identity(subject, null, null, clock.instant()))).isEqualTo("/welcome");
		assertThat(client.post("/api/auth/social/complete", profile(username))).hasStatus(HttpStatus.CREATED);
		return client;
	}

	private void link(ApiClient client, String provider, String subject) {
		assertThat(client.post("/api/account/identities/" + provider + "/link", "")).hasStatusOk();
		assertThat(callback(client, provider, identity(subject, null, null, null))).isEqualTo("/settings?linked=" + provider);
	}

	private ApiClient staleLineAccount() {
		ApiClient client = signedUp("alice@example.com");
		link(client, "line", "linked-line");
		ageSession();
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson()
			.extractingPath("$.recentlyAuthenticated").isEqualTo(false);
		return client;
	}

	private ApiClient staleGoogleAccount() {
		ApiClient client = new ApiClient(mvc);
		assertThat(callback(client, "google", identity("stale-google", "gina@example.com", true, clock.instant())))
			.isEqualTo("/welcome");
		assertThat(client.post("/api/auth/social/complete", profile("gina_google"))).hasStatus(HttpStatus.CREATED);
		ageSession();
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.recentlyAuthenticated").isEqualTo(false);
			assertThat(json).extractingPath("$.emailVerified").isEqualTo(true);
			assertThat(json).extractingPath("$.hasPassword").isEqualTo(false);
		});
		return client;
	}

	private void assertLineReauthenticationRejected(Instant authTime, String method) {
		ApiClient client = staleLineAccount();
		String id = userId(client);
		assertThat(client.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();

		assertThat(callback(client, "line", identity("linked-line", null, null, authTime, method)))
			.isEqualTo("/settings?error=reauth-not-fresh");
		assertProofAndUser(client, false, id);
	}

	private void assertProofAndUser(ApiClient client, boolean recent, String id) {
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.id").isEqualTo(id);
			assertThat(json).extractingPath("$.recentlyAuthenticated").isEqualTo(recent);
		});
	}

	private String userId(ApiClient client) {
		var result = client.get("/api/auth/me");
		assertThat(result).hasStatusOk();
		return ApiClient.read(result, "$.id");
	}

	private void ageSession() {
		clock.set(clock.instant().plusSeconds(660));
	}

	private static String profile(String username) {
		return """
				{"fullName": "Social User", "username": "%s"}""".formatted(username);
	}

}
