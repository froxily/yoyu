package com.shift28.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
class FakeOAuthCallbackController {

	static final String PATH = "/test/oauth/";

	private final OAuthHandlers handlers;

	FakeOAuthCallbackController(OAuthHandlers handlers) {
		this.handlers = handlers;
	}

	@PostMapping(PATH + "{provider}")
	Map<String, String> callback(@PathVariable String provider, @RequestBody Identity identity,
			HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("sub", identity.subject());
		claims.put("iss", SocialProviders.GOOGLE.equals(provider) ? "https://accounts.google.com" : "https://access.line.me");
		if (identity.email() != null) {
			claims.put("email", identity.email());
		}
		if (identity.emailVerified() != null) {
			claims.put("email_verified", identity.emailVerified());
		}
		if (identity.name() != null) {
			claims.put("name", identity.name());
		}
		if (identity.authTime() != null) {
			claims.put("auth_time", identity.authTime());
		}
		if (identity.amr() != null) {
			claims.put("amr", identity.amr());
		}
		Instant issuedAt = Instant.now();
		OidcIdToken token = new OidcIdToken("test-token", issuedAt, issuedAt.plusSeconds(300), claims);
		DefaultOidcUser user = new DefaultOidcUser(List.of(), token);
		return Map.of("redirect", handlers.handle(request, response, provider, user));
	}

	record Identity(String subject, String email, Boolean emailVerified, String name, Instant authTime,
			List<String> amr) {
	}

}
