package com.shift28.web;

import static com.shift28.web.LoginApiTest.login;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.Cookie;

@TestPropertySource(properties = { "public-origin=https://yoyu.example.test", "client-ip.header=X-Client-IP" })
class AppServiceProxyApiTest extends ApiTestSupport {

	private static final String ORIGIN = "https://yoyu.example.test";

	private static final String PROXY_ADDRESS = "203.0.113.50";

	@Test
	void forgedForwardedHeadersCannotChangeTheOAuthCallback() {
		for (String provider : new String[] { "google", "line" }) {
			MvcTestResult result = forged(mvc.get().uri("/oauth2/authorization/" + provider)).exchange();

			assertThat(result).hasStatus(HttpStatus.FOUND);
			String redirectUri = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
				.build()
				.getQueryParams()
				.getFirst("redirect_uri");
			assertThat(URLDecoder.decode(redirectUri, StandardCharsets.UTF_8))
				.isEqualTo(ORIGIN + "/login/oauth2/code/" + provider);
		}
	}

	@Test
	void redirectsStayOnThePublicOrigin() {
		MvcTestResult result = forged(mvc.get().uri("/login/oauth2/code/google?error=access_denied&state=unknown"))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FOUND);
		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(ORIGIN + "/login?error=oauth");
	}

	@Test
	void forwardedHeadersSayingHttpCannotTurnOffHttpsProtections() {
		MvcTestResult result = forged(mvc.get().uri("/api/health")).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getHeader("Strict-Transport-Security")).contains("max-age=");
		Cookie csrf = result.getResponse().getCookie(ApiClient.CSRF_COOKIE);
		assertThat(csrf).isNotNull();
		assertThat(csrf.getSecure()).isTrue();
	}

	@Test
	void theSessionCookieIsSecureOnThePublicOriginEvenWithoutTheCookieSetting() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.cookie(ApiClient.SESSION_COOKIE).getSecure()).isTrue();
		assertThat(alice.get("/api/auth/me")).hasStatusOk();
	}

	@Test
	void theAddressTheProxyAppendsLastDecidesTheRateLimitBucket() {
		ApiClient client = new ApiClient(mvc);
		assertThat(client.get("/api/health")).hasStatusOk();
		Cookie csrf = client.cookie(ApiClient.CSRF_COOKIE);
		String[][] forgedHeaders = { { "198.51.100.1, " + PROXY_ADDRESS }, { "198.51.100.2", PROXY_ADDRESS },
				{ "not-an-ip<script>, " + PROXY_ADDRESS }, { "198.51.100.4,, " + PROXY_ADDRESS },
				{ ", " + PROXY_ADDRESS } };
		for (String[] values : forgedHeaders) {
			assertThat(loginFrom(csrf, values)).as(String.join(" | ", values)).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		assertThat(loginFrom(csrf, "198.51.100.6, " + PROXY_ADDRESS)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(loginFrom(csrf, "198.51.100.6, 203.0.113.51")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult loginFrom(Cookie csrf, String... clientIpHeaders) {
		return mvc.post().uri("/api/auth/login").cookie(csrf).header(ApiClient.CSRF_HEADER, csrf.getValue())
			.header("X-Client-IP", (Object[]) clientIpHeaders)
			.contentType(MediaType.APPLICATION_JSON).content(login("unknown_client", PASSWORD)).exchange();
	}

	private static MockMvcRequestBuilder forged(MockMvcRequestBuilder request) {
		return request.header("X-Forwarded-Host", "forged.example")
			.header("X-Forwarded-Proto", "http")
			.header("X-Forwarded-Port", "8443")
			.header("X-Forwarded-Prefix", "/evil")
			.header("Forwarded", "for=198.51.100.9;host=forged2.example;proto=http");
	}

}
