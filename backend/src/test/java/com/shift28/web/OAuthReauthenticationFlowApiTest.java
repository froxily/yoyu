package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class OAuthReauthenticationFlowApiTest extends ApiTestSupport {

	@Autowired
	FakeTokenEndpoint tokens;

	@Test
	void reauthenticationRequestsLoginPromptAndMaximumAge() {
		ApiClient alice = preparedClient();

		MvcTestResult start = alice.get("/oauth2/authorization/line");

		assertThat(start).hasStatus(HttpStatus.FOUND);
		assertThat(query(start.getResponse().getHeader("Location")))
			.containsEntry("prompt", "login").containsEntry("max_age", "300");
	}

	@Test
	void freshPasswordReauthenticationIsRecent() {
		assertFreshReauthentication(0L, List.of("pwd"));
	}

	@Test
	void freshLineQrReauthenticationIsRecent() {
		assertFreshReauthentication(0L, List.of("lineqr"));
	}

	@Test
	void aForcedQrLoginWithoutAuthTimeIsFresh() {
		assertFreshReauthentication(null, List.of("lineqr"));
	}

	@Test
	void aForcedPasswordLoginWithoutAuthTimeIsFresh() {
		assertFreshReauthentication(null, List.of("pwd"));
	}

	@Test
	void missingAuthTimeAndMethodsIsNotFresh() {
		assertNotFresh("real-line", null, null);
	}

	@Test
	void missingAuthTimeWithoutAnInteractiveMethodIsNotFresh() {
		assertNotFresh("real-line", null, List.of());
	}

	@Test
	void missingAuthTimeWithAutoLoginIsNotFresh() {
		assertNotFresh("real-line", null, List.of("pwd", "lineautologin"));
	}

	@Test
	void missingAuthTimeWithSingleSignOnIsNotFresh() {
		assertNotFresh("real-line", null, List.of("linesso"));
	}

	@Test
	void aStaleTokenWithoutAuthTimeIsNotFresh() {
		ApiClient alice = preparedClient();

		assertThat(completeCallback(alice, "real-line", Map.of("amr", List.of("lineqr"), "iat",
				clock.instant().getEpochSecond() - 301))).isEqualTo("/settings?error=reauth-not-fresh");
		assertThat(alice.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.recentlyAuthenticated")
			.isEqualTo(false);
	}

	@Test
	void anOrdinaryLineSignInWithoutAuthTimeIsNotRecent() {
		preparedClient();
		ApiClient device = new ApiClient(mvc);

		assertThat(completeCallback(device, "real-line", Map.of("amr", List.of("pwd")))).isEqualTo("/");

		assertThat(device.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.recentlyAuthenticated")
			.isEqualTo(false);
		assertThat(device.post("/api/account/email", """
				{"email": "device@example.com"}""")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void aLineOnlyAccountConfirmsWithAForcedLineLoginThenAddsAnEmailAndAPassword() {
		ApiClient client = new ApiClient(mvc);
		assertThat(completeCallback(client, "line-only", Map.of("amr", List.of("lineqr")))).isEqualTo("/welcome");
		assertThat(client.post("/api/auth/social/complete", """
				{"fullName": "Line User", "username": "line_user"}""")).hasStatus(HttpStatus.CREATED);
		String email = """
				{"email": "line@example.com"}""";
		assertThat(client.post("/api/account/email", email)).hasStatus(HttpStatus.FORBIDDEN).bodyJson()
			.extractingPath("$.reauthenticate").isEqualTo(true);
		assertThat(client.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();

		assertThat(completeCallback(client, "line-only", Map.of("amr", List.of("lineqr"))))
			.isEqualTo("/settings?reauthenticated=line");

		String id = ApiClient.read(client.post("/api/account/email", email), "$.verificationId");
		assertThat(client.post("/api/account/email/verify", code(id, mail.lastCodeFor("line@example.com")))).hasStatusOk();
		assertThat(client.put("/api/account/password", """
				{"password": "another strong passphrase"}""")).hasStatusOk();
		assertThat(new ApiClient(mvc).post("/api/auth/login", LoginApiTest.login("line_user", "another strong passphrase")))
			.hasStatusOk();
	}

	@Test
	void oldAuthTimeIsNotFresh() {
		assertNotFresh("real-line", -301L, List.of("pwd"));
	}

	@Test
	void futureAuthTimeIsNotFresh() {
		assertNotFresh("real-line", 120L, List.of("pwd"));
	}

	@Test
	void lineAutoLoginIsNotFresh() {
		assertNotFresh("real-line", 0L, List.of("lineautologin"));
	}

	@Test
	void lineSsoIsNotFresh() {
		assertNotFresh("real-line", 0L, List.of("linesso"));
	}

	@Test
	void successfulCallbackCannotBeReplayed() {
		ApiClient alice = preparedClient();
		String callback = reauthenticationCallback(alice, Map.of("sub", "real-line", "auth_time",
				clock.instant().getEpochSecond(), "amr", List.of("pwd")));
		MvcTestResult first = alice.get(callback);
		assertThat(first.getResponse().getRedirectedUrl()).isEqualTo("/settings?reauthenticated=line");
		String id = ApiClient.read(alice.get("/api/auth/me"), "$.id");

		assertThat(alice.get(callback).getResponse().getRedirectedUrl()).isNotEqualTo("/settings?reauthenticated=line");
		assertThat(alice.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.id").isEqualTo(id);
	}

	@Test
	void freshProofExpires() {
		ApiClient alice = preparedClient();
		assertThat(completeCallback(alice, "real-line", Map.of("auth_time", clock.instant().getEpochSecond(),
				"amr", List.of("pwd")))).isEqualTo("/settings?reauthenticated=line");
		clock.set(clock.instant().plusSeconds(601));

		assertThat(alice.delete("/api/account/identities/line")).hasStatus(HttpStatus.FORBIDDEN).bodyJson()
			.extractingPath("$.reauthenticate").isEqualTo(true);
	}

	@Test
	void reauthenticationRequiresCsrfAndAuthentication() {
		ApiClient alice = preparedClient();
		MvcTestResult withoutCsrf = mvc.post().uri("/api/account/identities/line/reauthenticate")
			.contentType(MediaType.APPLICATION_JSON).cookie(alice.cookie(ApiClient.SESSION_COOKIE)).exchange();
		assertThat(withoutCsrf).hasStatus(HttpStatus.FORBIDDEN);

		ApiClient signedOut = new ApiClient(mvc);
		assertThat(signedOut.post("/api/account/identities/line/reauthenticate", ""))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void differentSubjectCannotReauthenticate() {
		assertNotFresh("other-line", 0L, List.of("pwd"), "/settings?error=identity-mismatch");
	}

	private void assertFreshReauthentication(Long authTimeOffsetSeconds, List<String> amr) {
		ApiClient alice = preparedClient();
		String id = ApiClient.read(alice.get("/api/auth/me"), "$.id");
		assertThat(completeCallback(alice, "real-line", claims(authTimeOffsetSeconds, amr)))
			.isEqualTo("/settings?reauthenticated=line");
		assertThat(alice.get("/api/auth/me")).hasStatusOk().bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.id").isEqualTo(id);
			assertThat(json).extractingPath("$.recentlyAuthenticated").isEqualTo(true);
		});
		assertThat(alice.delete("/api/account/identities/line")).hasStatus(HttpStatus.NO_CONTENT);
	}

	private void assertNotFresh(String subject, Long authTimeOffsetSeconds, List<String> amr) {
		assertNotFresh(subject, authTimeOffsetSeconds, amr, "/settings?error=reauth-not-fresh");
	}

	private void assertNotFresh(String subject, Long authTimeOffsetSeconds, List<String> amr, String redirect) {
		ApiClient alice = preparedClient();
		assertThat(completeCallback(alice, subject, claims(authTimeOffsetSeconds, amr))).isEqualTo(redirect);
		assertThat(alice.get("/api/auth/me")).hasStatusOk().bodyJson().extractingPath("$.recentlyAuthenticated")
			.isEqualTo(false);
		assertThat(alice.delete("/api/account/identities/line")).hasStatus(HttpStatus.FORBIDDEN).bodyJson()
			.extractingPath("$.reauthenticate").isEqualTo(true);
	}

	private Map<String, Object> claims(Long authTimeOffsetSeconds, List<String> amr) {
		Map<String, Object> claims = new LinkedHashMap<>();
		if (amr != null) {
			claims.put("amr", amr);
		}
		if (authTimeOffsetSeconds != null) {
			claims.put("auth_time", clock.instant().getEpochSecond() + authTimeOffsetSeconds);
		}
		return claims;
	}

	private ApiClient preparedClient() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/account/identities/line/link", "")).hasStatusOk();
		assertThat(completeCallback(alice, "real-line", Map.of("auth_time", clock.instant().getEpochSecond(),
				"amr", List.of("pwd")))).isEqualTo("/settings?linked=line");
		clock.set(clock.instant().plusSeconds(660));
		assertThat(alice.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();
		return alice;
	}

	private String reauthenticationCallback(ApiClient client, Map<String, Object> claims) {
		MvcTestResult start = client.get("/oauth2/authorization/line");
		String state = query(start.getResponse().getHeader("Location")).get("state");
		tokens.willIssue(claims);
		return "/login/oauth2/code/line?code=test-code&state=" + state;
	}

	private String completeCallback(ApiClient client, String subject, Map<String, Object> claims) {
		Map<String, Object> issued = new LinkedHashMap<>(claims);
		issued.put("sub", subject);
		String callback = reauthenticationCallback(client, issued);
		MvcTestResult result = client.get(callback);
		assertThat(result).hasStatus(HttpStatus.FOUND);
		return result.getResponse().getRedirectedUrl();
	}

	private static Map<String, String> query(String location) {
		return Arrays.stream(URI.create(location).getRawQuery().split("&"))
			.map(parameter -> parameter.split("=", 2))
			.collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
					pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
	}

}
