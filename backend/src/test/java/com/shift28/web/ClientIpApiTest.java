package com.shift28.web;

import static com.shift28.web.LoginApiTest.login;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import jakarta.servlet.http.Cookie;

class ClientIpApiTest extends ApiTestSupport {

	@Test
	void forgedHeadersCannotSplitTheFiftyAttemptSocketIpBucket() {
		ApiClient client = new ApiClient(mvc);
		assertThat(client.get("/api/health")).hasStatusOk();
		Cookie csrf = client.cookie(ApiClient.CSRF_COOKIE);
		for (int attempt = 0; attempt < 50; attempt++) {
			assertThat(loginWithForgedHeaders(csrf, attempt)).as("attempt %d", attempt + 1)
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}

		MvcTestResult result = loginWithForgedHeaders(csrf, 50);

		assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
		assertThat(count("users")).isZero();
	}

	private MvcTestResult loginWithForgedHeaders(Cookie csrf, int attempt) {
		return mvc.post().uri("/api/auth/login").cookie(csrf).header(ApiClient.CSRF_HEADER, csrf.getValue())
			.header("X-Client-IP", "198.51.100." + (attempt + 1))
			.header("X-Forwarded-For", "203.0.113." + (attempt + 1))
			.contentType(MediaType.APPLICATION_JSON).content(login("unknown_" + attempt, PASSWORD)).exchange();
	}

}
