package com.shift28.web;

import static com.shift28.web.SocialSignInApiTest.callback;
import static com.shift28.web.SocialSignInApiTest.identity;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class LoginApiTest extends ApiTestSupport {

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void usernameSignInIgnoresCase() {
		signedUp("alice@example.com");

		for (String identifier : new String[] { "alice", "ALICE", "AlIcE" }) {
			assertThat(new ApiClient(mvc).post("/api/auth/login", login(identifier, PASSWORD))).hasStatusOk()
				.bodyJson().extractingPath("$.username").isEqualTo("alice");
		}
	}

	@Test
	void emailSignInIgnoresCase() {
		signedUp("alice@example.com");

		for (String identifier : new String[] { "alice@example.com", "ALICE@EXAMPLE.COM", "Alice@Example.Com" }) {
			assertThat(new ApiClient(mvc).post("/api/auth/login", login(identifier, PASSWORD))).hasStatusOk()
				.bodyJson().extractingPath("$.email").isEqualTo("alice@example.com");
		}
	}

	@Test
	void wrongPasswordUnknownIdentifiersAndSocialOnlyAccountsHaveIdenticalResponses() throws Exception {
		signedUp("alice@example.com");
		ApiClient social = new ApiClient(mvc);
		assertThat(callback(social, "google", identity("google-social", "social@example.com", true, null)))
			.isEqualTo("/welcome");
		assertThat(social.post("/api/auth/social/complete", """
				{"fullName": "Social User", "username": "social_user"}""")).hasStatus(HttpStatus.CREATED);
		String expected = null;

		for (String body : new String[] { login("alice@example.com", "wrong password"),
				login("unknown@example.com", PASSWORD), login("unknown_user", PASSWORD),
				login("social@example.com", PASSWORD) }) {
			ApiClient client = new ApiClient(mvc);
			MvcTestResult result = client.post("/api/auth/login", body);
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.detail")
				.isEqualTo("Incorrect email, username or password.");
			String response = result.getResponse().getContentAsString();
			if (expected == null) {
				expected = response;
			}
			assertThat(response).isEqualTo(expected);
			assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		}
	}

	@Test
	void emailAndUsernameFailuresShareALockoutThatRejectsEitherCorrectIdentifier() {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);
		failFiveTimes(client);

		for (String identifier : new String[] { "alice@example.com", "ALICE" }) {
			MvcTestResult result = client.post("/api/auth/login", login(identifier, PASSWORD));
			assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
		}
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void unknownIdentifiersAreLimitedAfterFiveFailures() {
		ApiClient client = new ApiClient(mvc);
		for (String identifier : new String[] { "unknown@example.com", "unknown_user" }) {
			for (int attempt = 0; attempt < 5; attempt++) {
				assertThat(client.post("/api/auth/login", login(identifier, PASSWORD))).hasStatus(HttpStatus.UNAUTHORIZED);
			}
			MvcTestResult result = client.post("/api/auth/login", login(identifier.toUpperCase(), PASSWORD));
			assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
		}
	}

	@Test
	void failuresFromAnotherClientDoNotLockOutTheOwner() {
		ApiClient owner = signedUp("alice@example.com");
		ApiClient attacker = new ApiClient(mvc, "203.0.113.7");
		failFiveTimes(attacker);

		assertThat(attacker.post("/api/auth/login", login("alice", PASSWORD))).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(owner.post("/api/auth/login", login("alice", PASSWORD))).hasStatusOk();
		assertThat(new ApiClient(mvc, "198.51.100.9").post("/api/auth/login", login("alice@example.com", PASSWORD)))
			.hasStatusOk();
	}

	@Test
	void twentyFailuresFromAnyClientsLimitKnownAndUnknownIdentifiersAlikeForFifteenMinutes() {
		signedUp("alice@example.com");
		for (String identifier : new String[] { "alice", "unknown_user" }) {
			for (int client = 1; client <= 4; client++) {
				ApiClient attacker = new ApiClient(mvc, "203.0.113." + client);
				for (int attempt = 0; attempt < 5; attempt++) {
					assertThat(attacker.post("/api/auth/login", login(identifier, "wrong password")))
						.hasStatus(HttpStatus.UNAUTHORIZED);
				}
			}

			MvcTestResult result = new ApiClient(mvc, "198.51.100.9").post("/api/auth/login", login(identifier, PASSWORD));

			assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
		}
		clock.set(clock.instant().plusSeconds(900));
		assertThat(new ApiClient(mvc, "198.51.100.9").post("/api/auth/login", login("alice", PASSWORD))).hasStatusOk();
	}

	@Test
	void concurrentWrongPasswordsFromOneClientGetFiveAttemptsAtMost() throws Exception {
		signedUp("alice@example.com");
		List<ApiClient> clients = IntStream.range(0, 12).mapToObj(index -> new ApiClient(mvc, "203.0.113.7")).toList();
		clients.forEach(client -> client.get("/api/health"));
		ExecutorService executor = Executors.newFixedThreadPool(clients.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> attempts = clients.stream().map(client -> executor.submit(() -> {
				start.await();
				return client.post("/api/auth/login", login("alice", "wrong password")).getResponse().getStatus();
			})).toList();
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> attempt : attempts) {
				statuses.add(attempt.get(60, TimeUnit.SECONDS));
			}

			assertThat(statuses).filteredOn(status -> status == 401).hasSize(5);
			assertThat(statuses).filteredOn(status -> status == 429).hasSize(7);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void signInFailuresDoNotBlockTheSignedInOwnersReauthentication() {
		ApiClient owner = signedUp("alice@example.com");
		for (int client = 1; client <= 4; client++) {
			failFiveTimes(new ApiClient(mvc, "203.0.113." + client));
		}

		assertThat(owner.post("/api/auth/reauthenticate", """
				{"password": "%s"}""".formatted(PASSWORD))).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void accountLockoutExpiresAfterFifteenMinutes() {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);
		failFiveTimes(client);
		assertThat(client.post("/api/auth/login", login("alice", PASSWORD))).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		clock.set(clock.instant().plusSeconds(900));

		assertThat(client.post("/api/auth/login", login("alice", PASSWORD))).hasStatusOk();
		assertThat(client.get("/api/auth/me")).hasStatusOk();
	}

	@Test
	void legacyAccountMustVerifyItsEmailBeforeReceivingASession() {
		UUID id = insertLegacy();
		ApiClient client = new ApiClient(mvc);

		MvcTestResult result = client.post("/api/auth/login", login("legacy@example.com", PASSWORD));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.verificationRequired").isEqualTo(true);
			assertThat(json).extractingPath("$.verificationId").isNotNull();
		});
		assertThat(client.cookie(ApiClient.SESSION_COOKIE)).isNull();
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("spring_session")).isZero();
		assertThat(mail.sentTo("legacy@example.com")).hasSize(1);
		assertThat(client.post("/api/auth/login/verify",
				code(ApiClient.read(result, "$.verificationId"), mail.lastCodeFor("legacy@example.com"))))
			.hasStatusOk();
		assertThat(jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?").param(id)
			.query(Boolean.class).single()).isTrue();
		assertThat(client.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.id").isEqualTo(id.toString());
			assertThat(json).extractingPath("$.username").isNull();
			assertThat(json).extractingPath("$.emailVerified").isEqualTo(true);
		});
	}

	@Test
	void wrongLegacyPasswordDoesNotSendAnEmail() {
		insertLegacy();
		ApiClient client = new ApiClient(mvc);

		assertThat(client.post("/api/auth/login", login("legacy@example.com", "wrong password")))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mail.sent()).isEmpty();
		assertThat(count("email_codes")).isZero();
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void failedLegacyVerificationEmailIsRetryableAndNeverSignsInOrVerifies() {
		UUID id = insertLegacy();
		ApiClient client = new ApiClient(mvc);
		mail.failing(true);

		assertThat(client.post("/api/auth/login", login("legacy@example.com", PASSWORD)))
			.hasStatus(HttpStatus.SERVICE_UNAVAILABLE).bodyJson().extractingPath("$.retryable").isEqualTo(true);
		assertThat(client.cookie(ApiClient.SESSION_COOKIE)).isNull();
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("spring_session")).isZero();
		assertThat(jdbc.sql("SELECT email_verified_at IS NULL FROM users WHERE id = ?").param(id)
			.query(Boolean.class).single()).isTrue();
		assertThat(mail.sent()).isEmpty();
	}

	@Test
	void oldEmailRequestFieldStillSignsIn() {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);

		assertThat(client.post("/api/auth/login", credentials("alice@example.com", PASSWORD))).hasStatusOk();
		assertThat(client.get("/api/auth/me")).hasStatusOk();
	}

	static String login(String identifier, String password) {
		return """
				{"identifier": "%s", "password": "%s"}""".formatted(identifier, password);
	}

	private void failFiveTimes(ApiClient client) {
		for (int attempt = 0; attempt < 5; attempt++) {
			String identifier = attempt % 2 == 0 ? "alice@example.com" : "ALICE";
			assertThat(client.post("/api/auth/login", login(identifier, "wrong password"))).hasStatus(HttpStatus.UNAUTHORIZED);
		}
	}

	private UUID insertLegacy() {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)")
			.params(id, "legacy@example.com", passwordEncoder.encode(PASSWORD)).update();
		return id;
	}

}
