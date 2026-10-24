package com.shift28.web;

import java.time.Clock;
import java.util.function.Consumer;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.shift28.web.OAuthSession.Intent;
import com.shift28.web.OAuthSession.Kind;

final class OAuthRequestCustomizer implements Consumer<OAuth2AuthorizationRequest.Builder> {

	static final String GOOGLE_AUTH_TIME_CLAIM = "{\"id_token\":{\"auth_time\":{\"essential\":true}}}";

	private final Clock clock;

	OAuthRequestCustomizer(Clock clock) {
		this.clock = clock;
	}

	@Override
	public void accept(OAuth2AuthorizationRequest.Builder builder) {
		String[] registrationId = new String[1];
		builder.attributes(attributes -> registrationId[0] = (String) attributes.get(OAuth2ParameterNames.REGISTRATION_ID));
		boolean reauthenticate = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes current
				&& OAuthSession.peekIntent(current.getRequest(), clock) instanceof Intent intent
				&& intent.kind() == Kind.REAUTHENTICATE && intent.provider().equals(registrationId[0]);
		builder.additionalParameters(parameters -> {
			if (SocialProviders.GOOGLE.equals(registrationId[0])) {
				parameters.put("claims", GOOGLE_AUTH_TIME_CLAIM);
				if (reauthenticate) {
					parameters.put("prompt", "select_account");
				}
			}
			if (SocialProviders.LINE.equals(registrationId[0]) && reauthenticate) {
				parameters.put("prompt", "login");
				parameters.put("max_age", Long.toString(OAuthHandlers.FRESH.toSeconds()));
			}
		});
	}

}
