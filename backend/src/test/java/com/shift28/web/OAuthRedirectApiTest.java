package com.shift28.web;

import static com.shift28.web.SocialSignInApiTest.callback;
import static com.shift28.web.SocialSignInApiTest.identity;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class OAuthRedirectApiTest extends ApiTestSupport {

	@Test
	void googleAuthorizationRequestsCodePkceNonceAndAuthenticationTime() {
		String location = location(new ApiClient(mvc), "google");
		Map<String, String> query = query(location);

		assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
		assertThat(query).containsEntry("response_type", "code").containsEntry("client_id", "google-test-client")
			.containsEntry("code_challenge_method", "S256");
		assertThat(query.get("state")).isNotBlank();
		assertThat(query.get("nonce")).isNotBlank();
		assertThat(query.get("code_challenge")).isNotBlank();
		assertThat(query.get("scope").split(" ")).contains("openid");
		assertThat(query.get("claims")).isEqualTo("{\"id_token\":{\"auth_time\":{\"essential\":true}}}");
	}

	@Test
	void normalLineAuthorizationUsesPkceWithoutForcingLogin() {
		String location = location(new ApiClient(mvc), "line");
		Map<String, String> query = query(location);

		assertThat(location).startsWith("https://access.line.me/oauth2/v2.1/authorize?");
		assertThat(query).containsEntry("response_type", "code").containsEntry("client_id", "1234567890")
			.containsEntry("code_challenge_method", "S256");
		assertThat(query.get("state")).isNotBlank();
		assertThat(query.get("nonce")).isNotBlank();
		assertThat(query.get("code_challenge")).isNotBlank();
		assertThat(query.get("scope").split(" ")).containsExactlyInAnyOrder("openid", "profile");
		assertThat(query.get("prompt")).isNotEqualTo("login");
		assertThat(query.get("redirect_uri")).endsWith("/login/oauth2/code/line");
	}

	@Test
	void lineReauthenticationForcesAnImmediateLoginOnTheSameSession() {
		ApiClient client = signedUp("alice@example.com");
		assertThat(client.post("/api/account/identities/line/link", "")).hasStatusOk();
		assertThat(callback(client, "line", identity("redirect-line", null, null, null))).isEqualTo("/settings?linked=line");
		clock.set(clock.instant().plusSeconds(660));
		assertThat(client.post("/api/account/identities/line/reauthenticate", "")).hasStatusOk();

		Map<String, String> query = query(location(client, "line"));

		assertThat(query).containsEntry("prompt", "login").containsEntry("max_age", "300");
		assertThat(query.get("redirect_uri")).endsWith("/login/oauth2/code/line");
	}

	private String location(ApiClient client, String provider) {
		MvcTestResult result = client.get("/oauth2/authorization/" + provider);
		assertThat(result).hasStatus(HttpStatus.FOUND);
		String location = result.getResponse().getHeader("Location");
		assertThat(location).isNotBlank();
		return location;
	}

	private static Map<String, String> query(String location) {
		return Arrays.stream(URI.create(location).getRawQuery().split("&"))
			.map(parameter -> parameter.split("=", 2))
			.collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
					pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
	}

}
