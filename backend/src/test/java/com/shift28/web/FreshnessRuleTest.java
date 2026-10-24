package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class FreshnessRuleTest extends ApiTestSupport {

	@Autowired
	OAuthHandlers handlers;

	@Test
	void onlyLineCanUseTheForcedLoginFallbackWithoutAuthenticationTime() {
		DefaultOidcUser user = new DefaultOidcUser(List.of(), new OidcIdToken("token", clock.instant(),
				clock.instant().plusSeconds(300), Map.of("sub", "subject", "amr", List.of("pwd"), "iat", clock.instant())));

		assertThat(handlers.isFresh(SocialProviders.LINE, user, true)).isTrue();
		assertThat(handlers.isFresh(SocialProviders.GOOGLE, user, true)).isFalse();
		assertThat(handlers.isFresh(SocialProviders.LINE, user, false)).isFalse();
	}

	@Test
	void aFreshAuthenticationTimeCountsForEitherProvider() {
		DefaultOidcUser user = new DefaultOidcUser(List.of(), new OidcIdToken("token", clock.instant(),
				clock.instant().plusSeconds(300), Map.of("sub", "subject", "auth_time", clock.instant())));

		assertThat(handlers.isFresh(SocialProviders.GOOGLE, user, false)).isTrue();
		assertThat(handlers.isFresh(SocialProviders.LINE, user, false)).isTrue();
	}

}
