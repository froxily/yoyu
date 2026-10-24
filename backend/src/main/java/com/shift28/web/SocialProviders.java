package com.shift28.web;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
class SocialProviders {

	static final String GOOGLE = "google";

	static final String LINE = "line";

	private static final String CALLBACK = "/login/oauth2/code/{registrationId}";

	private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();

	SocialProviders(@Value("${oauth.google.client-id:}") String googleId,
			@Value("${oauth.google.client-secret:}") String googleSecret,
			@Value("${oauth.line.channel-id:}") String lineId, @Value("${oauth.line.channel-secret:}") String lineSecret,
			@Value("${oauth.line.email-scope:false}") boolean lineEmail, PublicOrigin origin) {
		String redirect = (origin.configured() ? origin.url() : "{baseUrl}") + CALLBACK;
		if (StringUtils.hasText(googleId) && StringUtils.hasText(googleSecret)) {
			registrations.put(GOOGLE, google(googleId, googleSecret, redirect));
		}
		if (StringUtils.hasText(lineId) && StringUtils.hasText(lineSecret)) {
			registrations.put(LINE, line(lineId, lineSecret, lineEmail, redirect));
		}
	}

	boolean enabled() {
		return !registrations.isEmpty();
	}

	boolean has(String provider) {
		return registrations.containsKey(provider);
	}

	ClientRegistrationRepository repository() {
		return new InMemoryClientRegistrationRepository(List.copyOf(registrations.values()));
	}

	@Bean
	JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(Clock clock) {
		OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
		factory.setJwsAlgorithmResolver(
				registration -> LINE.equals(registration.getRegistrationId()) ? MacAlgorithm.HS256 : SignatureAlgorithm.RS256);
		factory.setJwtValidatorFactory(registration -> {
			JwtTimestampValidator timestamps = new JwtTimestampValidator();
			timestamps.setClock(clock);
			OidcIdTokenValidator idToken = new OidcIdTokenValidator(registration);
			idToken.setClock(clock);
			return new DelegatingOAuth2TokenValidator<>(timestamps, idToken);
		});
		return factory;
	}

	private static ClientRegistration google(String clientId, String clientSecret, String redirect) {
		return ClientRegistration.withRegistrationId(GOOGLE)
			.clientId(clientId)
			.clientSecret(clientSecret)
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(redirect)
			.scope("openid", "email", "profile")
			.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
			.tokenUri("https://oauth2.googleapis.com/token")
			.jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
			.issuerUri("https://accounts.google.com")
			.userNameAttributeName(IdTokenClaimNames.SUB)
			.clientName("Google")
			.clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
			.build();
	}

	private static ClientRegistration line(String channelId, String channelSecret, boolean email, String redirect) {
		return ClientRegistration.withRegistrationId(LINE)
			.clientId(channelId)
			.clientSecret(channelSecret)
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(redirect)
			.scope(email ? List.of("openid", "profile", "email") : List.of("openid", "profile"))
			.authorizationUri("https://access.line.me/oauth2/v2.1/authorize")
			.tokenUri("https://api.line.me/oauth2/v2.1/token")
			.jwkSetUri("https://api.line.me/oauth2/v2.1/certs")
			.issuerUri("https://access.line.me")
			.userNameAttributeName(IdTokenClaimNames.SUB)
			.clientName("LINE")
			.clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
			.build();
	}

}
