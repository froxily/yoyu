package com.shift28.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.shift28.app.SocialAccountService;
import com.shift28.app.SocialAccountService.LinkResult;
import com.shift28.web.OAuthSession.Intent;
import com.shift28.web.OAuthSession.Kind;
import com.shift28.web.OAuthSession.PendingSignUp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
class OAuthHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuthHandlers.class);

	static final Duration FRESH = Duration.ofMinutes(5);

	private static final Set<String> NOT_FRESH_METHODS = Set.of("lineautologin", "linesso");

	private static final Set<String> INTERACTIVE_METHODS = Set.of("pwd", "lineqr", "mfa");

	private final SocialAccountService social;

	private final SessionSignIn signIn;

	private final Clock clock;

	OAuthHandlers(SocialAccountService social, SessionSignIn signIn, Clock clock) {
		this.social = social;
		this.signIn = signIn;
		this.clock = clock;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
		OidcUser user = (OidcUser) token.getPrincipal();
		response.sendRedirect(handle(request, response, token.getAuthorizedClientRegistrationId(), user));
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		Intent intent = OAuthSession.takeIntent(request, clock);
		response.sendRedirect(intent == null ? "/login?error=oauth" : "/settings?error=oauth");
	}

	String handle(HttpServletRequest request, HttpServletResponse response, String provider, OidcUser user) {
		String subject = user.getSubject();
		boolean forcedLogin = CallbackAuthorizationRequests.forcedLogin(request);
		boolean fresh = isFresh(provider, user, forcedLogin);
		Intent intent = OAuthSession.takeIntent(request, clock);
		if (intent != null) {
			if (intent.kind() == Kind.REAUTHENTICATE && !fresh) {
				log.info("{} re-authentication was not fresh: {}, login {}", provider, freshnessEvidence(user),
						forcedLogin ? "forced" : "not forced");
			}
			return handleIntent(request, intent, provider, user, fresh);
		}
		Optional<UUID> linked = social.linkedUser(provider, subject);
		if (linked.isPresent()) {
			OAuthSession.clearPending(request);
			signIn.signIn(linked.get(), fresh, request, response);
			return "/";
		}
		String verifiedEmail = SocialProviders.GOOGLE.equals(provider) && Boolean.TRUE.equals(user.getEmailVerified())
				&& StringUtils.hasText(user.getEmail()) ? user.getEmail() : null;
		if (social.emailHasAccount(verifiedEmail)) {
			return "/login?error=link-required";
		}
		String unverifiedEmail = verifiedEmail == null && StringUtils.hasText(user.getEmail()) ? user.getEmail() : null;
		OAuthSession.savePending(request, new PendingSignUp(provider, subject, suggestedName(user), verifiedEmail,
				unverifiedEmail, fresh, clock.instant().plus(OAuthSession.LIFETIME)));
		return "/welcome";
	}

	private String handleIntent(HttpServletRequest request, Intent intent, String provider, OidcUser user,
			boolean fresh) {
		String subject = user.getSubject();
		UUID current = signIn.signedInUser(request);
		if (!intent.provider().equals(provider) || current == null || !current.equals(intent.userId())) {
			return "/settings?error=oauth";
		}
		if (intent.kind() == Kind.LINK) {
			LinkResult result = social.link(current, provider, subject);
			return switch (result) {
				case LINKED -> "/settings?linked=" + provider;
				case IDENTITY_IN_USE -> "/settings?error=identity-in-use";
				case PROVIDER_ALREADY_LINKED -> "/settings?error=provider-already-linked";
			};
		}
		if (!social.linkedUser(provider, subject).filter(current::equals).isPresent()) {
			return "/settings?error=identity-mismatch";
		}
		if (!fresh) {
			return SocialProviders.GOOGLE.equals(provider) && user.getAuthenticatedAt() == null
					? "/settings?error=reauth-google-no-time" : "/settings?error=reauth-not-fresh";
		}
		signIn.markProved(request);
		return "/settings?reauthenticated=" + provider;
	}

	boolean isFresh(String provider, OidcUser user, boolean forcedLogin) {
		List<String> methods = user.getAuthenticationMethods();
		if (methods != null && methods.stream().anyMatch(NOT_FRESH_METHODS::contains)) {
			return false;
		}
		if (user.getAuthenticatedAt() != null) {
			return recent(user.getAuthenticatedAt());
		}
		return SocialProviders.LINE.equals(provider) && forcedLogin && methods != null
				&& methods.stream().anyMatch(INTERACTIVE_METHODS::contains)
				&& user.getIssuedAt() != null && recent(user.getIssuedAt());
	}

	private boolean recent(Instant at) {
		Instant now = clock.instant();
		return !at.isBefore(now.minus(FRESH)) && !at.isAfter(now.plus(Duration.ofMinutes(1)));
	}

	private String freshnessEvidence(OidcUser user) {
		Instant authenticatedAt = user.getAuthenticatedAt();
		long age = authenticatedAt == null ? 0 : Duration.between(authenticatedAt, clock.instant()).toSeconds();
		String time = authenticatedAt == null ? "auth_time absent"
				: age < 0 ? "auth_time " + -age + "s in the future" : "auth_time " + age + "s old";
		List<String> methods = user.getAuthenticationMethods();
		String issued = user.getIssuedAt() == null ? "iat absent"
				: "iat " + Duration.between(user.getIssuedAt(), clock.instant()).toSeconds() + "s old";
		return time + ", " + issued + ", amr " + (methods == null ? "absent" : methods.stream()
			.map(method -> method.replaceAll("[^A-Za-z0-9_-]", ""))
			.limit(5)
			.toList());
	}

	private static String suggestedName(OidcUser user) {
		String name = StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getClaimAsString("name");
		if (!StringUtils.hasText(name)) {
			return null;
		}
		String trimmed = name.strip();
		return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
	}

}
