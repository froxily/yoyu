package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shift28.IntegrationTest;
import com.shift28.MutableClock;
import com.shift28.TestClockConfiguration;

@IntegrationTest
class IdTokenDecoderTest {

	@Autowired
	JwtDecoderFactory<ClientRegistration> decoders;

	@Autowired
	SocialProviders providers;

	@Autowired
	MutableClock clock;

	private ClientRegistration line;

	@BeforeEach
	void setUp() {
		clock.set(TestClockConfiguration.DEFAULT_NOW);
		line = providers.repository().findByRegistrationId("line");
	}

	@Test
	void validLineHs256TokenDecodesWithTheConfiguredChannelSecret() throws JOSEException {
		assertThat(line.getClientSecret()).isEqualTo("line-test-channel-secret-0123456789");
		String token = token(line.getClientSecret(), "https://access.line.me", "1234567890",
				clock.instant().minusSeconds(30), clock.instant().plusSeconds(300));

		Jwt decoded = decoders.createDecoder(line).decode(token);

		assertThat(decoded.getSubject()).isEqualTo("line-subject");
		assertThat(decoded.getAudience()).containsExactly("1234567890");
		assertThat(decoded.getIssuer().toString()).isEqualTo("https://access.line.me");
		assertThat(decoded.getHeaders()).containsEntry("alg", "HS256");
	}

	@Test
	void lineTokenSignedWithTheWrongSecretIsRejected() throws JOSEException {
		String token = token("wrong-channel-secret-0123456789abcdef", "https://access.line.me", "1234567890",
				clock.instant().minusSeconds(30), clock.instant().plusSeconds(300));

		assertThatThrownBy(() -> decoders.createDecoder(line).decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void lineTokenForTheWrongAudienceIsRejected() throws JOSEException {
		String token = token(line.getClientSecret(), "https://access.line.me", "another-channel",
				clock.instant().minusSeconds(30), clock.instant().plusSeconds(300));

		assertThatThrownBy(() -> decoders.createDecoder(line).decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void lineTokenFromTheWrongIssuerIsRejected() throws JOSEException {
		String token = token(line.getClientSecret(), "https://wrong.example.com", "1234567890",
				clock.instant().minusSeconds(30), clock.instant().plusSeconds(300));

		assertThatThrownBy(() -> decoders.createDecoder(line).decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void expiredLineTokenIsRejected() throws JOSEException {
		String token = token(line.getClientSecret(), "https://access.line.me", "1234567890",
				clock.instant().minusSeconds(600), clock.instant().minusSeconds(120));

		assertThatThrownBy(() -> decoders.createDecoder(line).decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void googleDecoderRejectsHs256EvenWhenSignedWithItsClientSecret() throws JOSEException {
		ClientRegistration google = providers.repository().findByRegistrationId("google");
		String token = token(google.getClientSecret(), "https://accounts.google.com", google.getClientId(),
				clock.instant().minusSeconds(30), clock.instant().plusSeconds(300));

		assertThatThrownBy(() -> decoders.createDecoder(google).decode(token)).isInstanceOf(JwtException.class)
			.hasMessageContaining("algorithm");
	}

	private static String token(String secret, String issuer, String audience, Instant issuedAt, Instant expiresAt)
			throws JOSEException {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("line-subject").issuer(issuer).audience(audience)
			.issueTime(Date.from(issuedAt)).expirationTime(Date.from(expiresAt)).build();
		SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		byte[] key = secret.getBytes(StandardCharsets.UTF_8);
		token.sign(new MACSigner(Arrays.copyOf(key, Math.max(32, key.length))));
		return token.serialize();
	}

}
