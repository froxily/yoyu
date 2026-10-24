package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class OAuthCallbackFlowApiTest extends ApiTestSupport {

	@Autowired
	FakeTokenEndpoint tokens;

	@Test
	void linkingThroughTheRealCallbackReturnsToSettingsWithTheIdentityLinked() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/account/identities/line/link", "")).hasStatusOk();
		String session = alice.cookie(ApiClient.SESSION_COOKIE).getValue();
		String csrf = alice.cookie(ApiClient.CSRF_COOKIE).getValue();

		assertThat(signInWithLine(alice, "real-line")).isEqualTo("/settings?linked=line");

		assertThat(alice.cookie(ApiClient.SESSION_COOKIE).getValue()).isNotEqualTo(session);
		assertThat(alice.cookie(ApiClient.CSRF_COOKIE).getValue()).isNotEqualTo(csrf);
		assertThat(jdbc.sql("SELECT count(*) FROM spring_session_attributes WHERE attribute_bytes IS NOT NULL"
				+ " AND attribute_name = 'SPRING_SECURITY_CONTEXT' AND position('OAuth2AuthenticationToken' in"
				+ " encode(attribute_bytes, 'escape')) > 0").query(Integer.class).single()).isZero();
		assertThat(alice.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.email").isEqualTo("alice@example.com");
			assertThat(json).extractingPath("$.identities[0].provider").isEqualTo("line");
		});
		assertThat(count("user_identities")).isOne();
	}

	@Test
	void aLinkedIdentitySignsInThroughTheRealCallback() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/account/identities/line/link", "")).hasStatusOk();
		assertThat(signInWithLine(alice, "real-line")).isEqualTo("/settings?linked=line");
		ApiClient device = new ApiClient(mvc);

		assertThat(signInWithLine(device, "real-line")).isEqualTo("/");

		assertThat(device.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.email")
			.isEqualTo("alice@example.com");
	}

	@Test
	void reauthenticatingThroughTheRealCallbackKeepsTheAccountSignedInAndRecent() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/account/identities/line/link", "")).hasStatusOk();
		assertThat(signInWithLine(alice, "real-line")).isEqualTo("/settings?linked=line");
		clock.set(clock.instant().plusSeconds(660));
		assertThat(alice.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();

		assertThat(signInWithLine(alice, "real-line")).isEqualTo("/settings?reauthenticated=line");

		assertThat(alice.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.recentlyAuthenticated")
			.isEqualTo(true);
	}

	@Test
	void cancellingAtTheProviderWhileLinkingKeepsTheAccountSignedIn() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/account/identities/line/link", "")).hasStatusOk();
		MvcTestResult start = alice.get("/oauth2/authorization/line");
		String state = query(start.getResponse().getHeader("Location")).get("state");

		MvcTestResult callback = alice.get("/login/oauth2/code/line?error=access_denied&state=" + state);

		assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo("/settings?error=oauth");
		assertThat(alice.get("/api/auth/me")).hasStatusOk();
		assertThat(count("user_identities")).isZero();
	}

	@Test
	void aNewIdentityKeepsItsPendingSignUpThroughTheRealCallback() {
		ApiClient client = new ApiClient(mvc);

		assertThat(signInWithLine(client, "new-line")).isEqualTo("/welcome");

		assertThat(client.get("/api/auth/social/pending")).hasStatusOk().bodyJson().extractingPath("$.provider")
			.isEqualTo("line");
		assertThat(client.post("/api/auth/social/complete", """
				{"fullName": "Line User", "username": "line_user"}""")).hasStatus(HttpStatus.CREATED);
		assertThat(client.get("/api/auth/me")).hasStatusOk();
	}

	private String signInWithLine(ApiClient client, String subject) {
		MvcTestResult start = client.get("/oauth2/authorization/line");
		assertThat(start).hasStatus(HttpStatus.FOUND);
		String state = query(start.getResponse().getHeader("Location")).get("state");
		tokens.willIssue(Map.of("sub", subject, "auth_time", clock.instant().getEpochSecond(), "amr", List.of("pwd")));

		MvcTestResult callback = client.get("/login/oauth2/code/line?code=test-code&state=" + state);

		assertThat(callback).hasStatus(HttpStatus.FOUND);
		return callback.getResponse().getRedirectedUrl();
	}

	private static Map<String, String> query(String location) {
		return Arrays.stream(URI.create(location).getRawQuery().split("&"))
			.map(parameter -> parameter.split("=", 2))
			.collect(java.util.stream.Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
					pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
	}

}
