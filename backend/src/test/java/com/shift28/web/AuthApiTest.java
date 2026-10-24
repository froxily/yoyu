package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import jakarta.servlet.http.Cookie;

class AuthApiTest extends ApiTestSupport {

	@Test
	void registerEmailsACodeAndOnlyCreatesTheAccountOnceItIsEntered() {
		ApiClient client = new ApiClient(mvc);

		String verificationId = startRegistration(client, "alice@example.com", "alice");

		assertThat(count("users")).isZero();
		assertThat(jdbc.sql("SELECT length(code_hash) FROM email_codes").query(Integer.class).single()).isEqualTo(32);
		String code = mail.lastCodeFor("alice@example.com");
		assertThat(mail.sentTo("alice@example.com")).singleElement()
			.satisfies(message -> assertThat(message.subject()).isEqualTo("Yoyū: Your sign-up code"));
		assertThat(jdbc.sql("SELECT count(*) FROM email_codes WHERE encode(code_hash, 'escape') LIKE ?")
			.param("%" + code + "%")
			.query(Long.class)
			.single()).isZero();
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);

		assertThat(client.post("/api/auth/register/verify", code(verificationId, code))).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo("alice@example.com");
		assertThat(client.get("/api/auth/me")).hasStatusOk()
			.bodyJson()
			.satisfies(json -> {
				assertThat(json).extractingPath("$.email").isEqualTo("alice@example.com");
				assertThat(json).extractingPath("$.username").isEqualTo("alice");
				assertThat(json).extractingPath("$.fullName").isEqualTo("Test User");
				assertThat(json).extractingPath("$.emailVerified").isEqualTo(true);
				assertThat(json).extractingPath("$.hasPassword").isEqualTo(true);
			});
		assertThat(jdbc.sql("SELECT password_hash FROM users").query(String.class).single()).startsWith("$2a$")
			.doesNotContain(PASSWORD);
		assertThat(count("email_codes")).isZero();
		assertThat(client.post("/api/auth/register/verify", code(verificationId, code)))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void registeringATakenEmailLooksTheSameButCreatesNothingAndWarnsTheOwner() {
		signedUp("alice@example.com");
		mail.clear();
		ApiClient stranger = new ApiClient(mvc);

		MvcTestResult started = stranger.post("/api/auth/register",
				registration("ALICE@example.com", "someone_else", PASSWORD));

		assertThat(started).hasStatus(HttpStatus.ACCEPTED)
			.bodyJson()
			.satisfies(json -> {
				assertThat(json).extractingPath("$.verificationId").isNotNull();
				assertThat(json).extractingPath("$.resendAfterSeconds").isEqualTo(60);
			});
		assertThat(mail.sentTo("alice@example.com")).singleElement()
			.satisfies(message -> assertThat(message.body()).contains("already have one").doesNotContainPattern("\\d{6}"));
		for (String guess : new String[] { "000000", "123456", "999999" }) {
			assertThat(stranger.post("/api/auth/register/verify",
					code(ApiClient.read(started, "$.verificationId"), guess)))
				.hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(count("users")).isOne();
	}

	@Test
	void registrationValidatesEveryField() {
		ApiClient client = new ApiClient(mvc);

		assertThat(client.post("/api/auth/register", registration("not-an-email", "bob", PASSWORD)))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("email");
		assertThat(client.post("/api/auth/register", registration("bob@example.com", "bob", "123456789")))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].field")
			.isEqualTo("password");
		assertThat(client.post("/api/auth/register", registration("bob@example.com", "bob", "é".repeat(37))))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.post("/api/auth/register", registration("bob@example.com", "bob", "1234567890")))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.errors[0].message")
			.isEqualTo("This password is too common. Choose another.");
		assertThat(client.post("/api/auth/register", registration("bob@example.com", "bob", "bob@example.com")))
			.hasStatus(HttpStatus.BAD_REQUEST);
		for (String username : new String[] { "ab", "has space", "x".repeat(21), "admin", "dot.ted" }) {
			assertThat(client.post("/api/auth/register", registration("bob@example.com", username, PASSWORD)))
				.as(username)
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.errors[0].field")
				.isEqualTo("username");
		}
		assertThat(client.post("/api/auth/register", """
				{"fullName": "  ", "username": "bob", "email": "bob@example.com", "password": "%s"}""".formatted(PASSWORD)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.post("/api/auth/register", registration("bob@example.com", "bob", "1234567890ab")))
			.hasStatus(HttpStatus.ACCEPTED);
		assertThat(count("users")).isZero();
	}

	@Test
	void registrationRejectsATakenUsernameIgnoringCase() {
		signedUp("alice@example.com");

		assertThat(new ApiClient(mvc).post("/api/auth/register", registration("other@example.com", "ALICE", PASSWORD)))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.satisfies(json -> {
				assertThat(json).extractingPath("$.detail").isEqualTo("That username is taken.");
				assertThat(json).extractingPath("$.errors[0].field").isEqualTo("username");
			});
	}

	@Test
	void loginStartsANewSessionAndLogoutEndsIt() {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);

		assertThat(client.post("/api/auth/login", credentials("Alice@Example.com", PASSWORD))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.email")
			.isEqualTo("alice@example.com");
		Cookie firstSession = client.cookie(ApiClient.SESSION_COOKIE);
		assertThat(client.post("/api/auth/login", credentials("alice@example.com", PASSWORD))).hasStatusOk();
		assertThat(client.cookie(ApiClient.SESSION_COOKIE).getValue()).isNotEqualTo(firstSession.getValue());
		assertThat(mvc.get().uri("/api/auth/me").cookie(firstSession)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.get("/api/auth/me")).hasStatusOk();
		Cookie signedIn = client.cookie(ApiClient.SESSION_COOKIE);
		assertThat(storedSessions(signedIn)).isOne();

		assertThat(client.post("/api/auth/logout", "")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client.cookie(ApiClient.SESSION_COOKIE)).isNull();
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(storedSessions(signedIn)).isZero();
	}

	@Test
	void loginRejectsWrongPasswordsAndUnknownEmailsAlike() {
		signedUp("alice@example.com");
		ApiClient client = new ApiClient(mvc);

		for (String body : new String[] { credentials("alice@example.com", "wrong password"),
				credentials("nobody@example.com", PASSWORD), credentials("alice@example.com", "x".repeat(100)) }) {
			assertThat(client.post("/api/auth/login", body)).hasStatus(HttpStatus.UNAUTHORIZED)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Incorrect email, username or password.");
		}
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void apiRequiresSignInExceptHealth() {
		ApiClient anonymous = new ApiClient(mvc);

		assertThat(anonymous.get("/api/jobs")).hasStatus(HttpStatus.UNAUTHORIZED)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo(401);
		assertThat(anonymous.get("/api/health")).hasStatusOk();
	}

	@Test
	void unsafeRequestsWithoutTheCsrfTokenAreForbidden() {
		ApiClient client = signedUp("alice@example.com");

		MvcTestResult withoutToken = mvc.post()
			.uri("/api/jobs")
			.cookie(client.cookie(ApiClient.SESSION_COOKIE))
			.contentType(MediaType.APPLICATION_JSON)
			.content(job("Cafe", 1200))
			.exchange();

		assertThat(withoutToken).hasStatus(HttpStatus.FORBIDDEN)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Missing or invalid CSRF token.");
		assertThat(count("jobs")).isZero();
	}

	@Test
	void spaReadsTheCsrfCookieAndSendsItBackAsAHeader() {
		MvcTestResult first = mvc.get().uri("/api/auth/me").exchange();
		Cookie token = first.getResponse().getCookie("XSRF-TOKEN");

		assertThat(first).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(token).isNotNull();
		assertThat(token.isHttpOnly()).isFalse();

		MvcTestResult started = mvc.post()
			.uri("/api/auth/register")
			.cookie(token)
			.header("X-XSRF-TOKEN", token.getValue())
			.contentType(MediaType.APPLICATION_JSON)
			.content(registration("alice@example.com", "alice", PASSWORD))
			.exchange();
		assertThat(started).hasStatus(HttpStatus.ACCEPTED);

		MvcTestResult registered = mvc.post()
			.uri("/api/auth/register/verify")
			.cookie(token)
			.header("X-XSRF-TOKEN", token.getValue())
			.contentType(MediaType.APPLICATION_JSON)
			.content(code(ApiClient.read(started, "$.verificationId"), mail.lastCodeFor("alice@example.com")))
			.exchange();

		Cookie rotated = ApiClient.lastCsrfCookie(registered);

		assertThat(registered).hasStatus(HttpStatus.CREATED);
		assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(token.getValue());

		MvcTestResult created = mvc.post()
			.uri("/api/jobs")
			.cookie(registered.getResponse().getCookie(ApiClient.SESSION_COOKIE), rotated)
			.header("X-XSRF-TOKEN", rotated.getValue())
			.contentType(MediaType.APPLICATION_JSON)
			.content(job("Cafe", 1200))
			.exchange();

		assertThat(created).hasStatus(HttpStatus.CREATED);
	}

}
