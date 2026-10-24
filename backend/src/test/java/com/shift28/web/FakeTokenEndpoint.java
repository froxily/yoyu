package com.shift28.web;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Component
class FakeTokenEndpoint implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

	private final Clock clock;

	private volatile Map<String, Object> claims = Map.of();

	FakeTokenEndpoint(Clock clock) {
		this.clock = clock;
	}

	void willIssue(Map<String, Object> claims) {
		this.claims = claims;
	}

	@Override
	public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest grant) {
		ClientRegistration registration = grant.getClientRegistration();
		OAuth2AuthorizationRequest authorization = grant.getAuthorizationExchange().getAuthorizationRequest();
		Instant now = clock.instant();
		JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
			.issuer(registration.getProviderDetails().getIssuerUri())
			.audience(registration.getClientId())
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(300)))
			.claim(OidcParameterNames.NONCE, authorization.getAdditionalParameters().get(OidcParameterNames.NONCE));
		claims.forEach(builder::claim);
		SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
		try {
			token.sign(new MACSigner(registration.getClientSecret().getBytes(StandardCharsets.UTF_8)));
		}
		catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
		return OAuth2AccessTokenResponse.withToken("test-access-token")
			.tokenType(OAuth2AccessToken.TokenType.BEARER)
			.expiresIn(300)
			.scopes(authorization.getScopes())
			.additionalParameters(Map.of(OidcParameterNames.ID_TOKEN, token.serialize()))
			.build();
	}

}
