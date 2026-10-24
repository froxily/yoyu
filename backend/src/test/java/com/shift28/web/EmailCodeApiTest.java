package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class EmailCodeApiTest extends ApiTestSupport {

	@Test
	void fiveWrongCodesLockOutEvenTheCorrectCode() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		String correct = mail.lastCodeFor("alice@example.com");
		String wrong = correct.equals("000000") ? "000001" : "000000";

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThat(client.post("/api/auth/register/verify", code(id, wrong))).hasStatus(HttpStatus.BAD_REQUEST);
		}

		assertThat(client.post("/api/auth/register/verify", code(id, correct))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(count("users")).isZero();
		assertThat(jdbc.sql("SELECT attempts FROM email_codes WHERE id = ?").param(UUID.fromString(id))
			.query(Integer.class).single()).isEqualTo(5);
	}

	@Test
	void codeExpiresAtExactlyTenMinutes() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		clock.set(clock.instant().plusSeconds(600));

		assertThat(client.post("/api/auth/register/verify", code(id, mail.lastCodeFor("alice@example.com"))))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(count("users")).isZero();
	}

	@Test
	void codeCanOnlyBeUsedOnce() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		String body = code(id, mail.lastCodeFor("alice@example.com"));

		assertThat(client.post("/api/auth/register/verify", body)).hasStatus(HttpStatus.CREATED);
		assertThat(client.post("/api/auth/register/verify", body)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(count("users")).isOne();
	}

	@Test
	void resendDuringCooldownReportsTheRemainingSeconds() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		clock.set(clock.instant().plusSeconds(10));

		MvcTestResult result = client.post("/api/auth/codes/resend", resend(id));

		assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson()
			.extractingPath("$.retryAfterSeconds").isEqualTo(50);
		assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("50");
		assertThat(mail.sentTo("alice@example.com")).hasSize(1);
	}

	@Test
	void resendAfterCooldownReplacesTheOldCode() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		String oldCode = mail.lastCodeFor("alice@example.com");
		clock.set(clock.instant().plusSeconds(61));

		assertThat(client.post("/api/auth/codes/resend", resend(id))).hasStatus(HttpStatus.ACCEPTED);
		String newCode = mail.lastCodeFor("alice@example.com");
		assertThat(mail.sentTo("alice@example.com")).hasSize(2);
		assertThat(newCode).isNotEqualTo(oldCode);
		assertThat(client.post("/api/auth/register/verify", code(id, oldCode))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.post("/api/auth/register/verify", code(id, newCode))).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void sixthSendIsRejectedAfterFiveSuccessfulSends() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		for (int send = 1; send < 5; send++) {
			clock.set(clock.instant().plusSeconds(61));
			assertThat(client.post("/api/auth/codes/resend", resend(id))).hasStatus(HttpStatus.ACCEPTED);
		}
		clock.set(clock.instant().plusSeconds(61));

		assertThat(client.post("/api/auth/codes/resend", resend(id))).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(mail.sentTo("alice@example.com")).hasSize(5);
	}

	@Test
	void registrationCodeCannotVerifyALogin() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		String body = code(id, mail.lastCodeFor("alice@example.com"));

		assertThat(client.post("/api/auth/login/verify", body)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.post("/api/auth/register/verify", body)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void registrationCodeCannotResetAPassword() {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		String value = mail.lastCodeFor("alice@example.com");

		assertThat(client.post("/api/auth/password/reset", """
				{"verificationId": "%s", "code": "%s", "password": "%s"}""".formatted(id, value, PASSWORD)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.post("/api/auth/register/verify", code(id, value))).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void anotherUserCannotResendAnEmailChangeCode() {
		ApiClient alice = signedUp("alice@example.com");
		ApiClient bob = signedUp("bob@example.com");
		MvcTestResult started = alice.post("/api/account/email", """
				{"email": "new@example.com"}""");
		assertThat(started).hasStatus(HttpStatus.ACCEPTED);
		String id = ApiClient.read(started, "$.verificationId");
		clock.set(clock.instant().plusSeconds(61));

		assertThat(bob.post("/api/auth/codes/resend", resend(id))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mail.sentTo("new@example.com")).hasSize(1);
		assertThat(alice.post("/api/auth/codes/resend", resend(id))).hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void registrationAndVerificationNeverLogTheCode(CapturedOutput output) {
		ApiClient client = new ApiClient(mvc);
		String id = startRegistration(client, "alice@example.com", "alice");
		String value = mail.lastCodeFor("alice@example.com");

		assertThat(client.post("/api/auth/register/verify", code(id, value))).hasStatus(HttpStatus.CREATED);
		assertThat(output.getAll()).doesNotContain(value);
	}

	@Test
	void databaseStoresAThirtyTwoByteHashAndNoPlaintextCode() {
		String id = startRegistration(new ApiClient(mvc), "alice@example.com", "alice");
		String value = mail.lastCodeFor("alice@example.com");

		assertThat(jdbc.sql("SELECT code_hash FROM email_codes WHERE id = ?").param(UUID.fromString(id))
			.query(byte[].class).single()).hasSize(32);
		assertThat(jdbc.sql("SELECT row_to_json(email_codes)::text FROM email_codes WHERE id = ?")
			.param(UUID.fromString(id)).query(String.class).single()).doesNotContain(value);
		assertThat(jdbc.sql("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = current_schema() AND table_name = 'email_codes'""")
			.query(String.class).list()).contains("code_hash").doesNotContain("code", "plaintext_code");
	}

	private static String resend(String id) {
		return """
				{"verificationId": "%s"}""".formatted(id);
	}

}
