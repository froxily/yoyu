package com.shift28.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.shift28.app.AccountService;
import com.shift28.app.AccountUser;
import com.shift28.app.ReauthenticationRequiredException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
class SessionSignIn {

	static final Duration RECENT = Duration.ofMinutes(10);

	private static final String PROVED_AT = SessionSignIn.class.getName() + ".provedAt";

	private final AuthenticationManager authenticationManager;

	private final AccountService accounts;

	private final SessionAuthenticationStrategy sessionStrategy;

	private final SecurityContextRepository contextRepository;

	private final Clock clock;

	private final int signedInTimeoutSeconds;

	SessionSignIn(AuthenticationManager authenticationManager, AccountService accounts,
			SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository, Clock clock,
			@Value("${session.signed-in-timeout}") Duration signedInTimeout) {
		this.authenticationManager = authenticationManager;
		this.accounts = accounts;
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
		this.clock = clock;
		this.signedInTimeoutSeconds = Math.toIntExact(signedInTimeout.toSeconds());
	}

	AccountUser checkPassword(String identifier, String password) {
		Authentication authentication = authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(identifier, password));
		return (AccountUser) authentication.getPrincipal();
	}

	void signIn(UUID userId, boolean proved, HttpServletRequest request, HttpServletResponse response) {
		signIn(accounts.loadById(userId), proved, request, response);
	}

	void signIn(AccountUser user, boolean proved, HttpServletRequest request, HttpServletResponse response) {
		user.eraseCredentials();
		UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(user,
				null, user.getAuthorities());
		authentication.eraseCredentials();
		sessionStrategy.onAuthentication(authentication, request, response);
		CsrfCookieFilter.loadToken(request);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		HttpSession session = request.getSession();
		session.setMaxInactiveInterval(signedInTimeoutSeconds);
		if (proved) {
			session.setAttribute(PROVED_AT, clock.instant());
		}
		else {
			session.removeAttribute(PROVED_AT);
		}
	}

	UUID signedInUser(HttpServletRequest request) {
		Authentication authentication = contextRepository.loadDeferredContext(request).get().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof UserDetails user)) {
			return null;
		}
		return UUID.fromString(user.getUsername());
	}

	void markProved(HttpServletRequest request) {
		request.getSession().setAttribute(PROVED_AT, clock.instant());
	}

	boolean recentlyProved(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(PROVED_AT) instanceof Instant provedAt)) {
			return false;
		}
		Instant now = clock.instant();
		return !provedAt.isAfter(now) && provedAt.plus(RECENT).isAfter(now);
	}

	void requireRecentProof(HttpServletRequest request) {
		if (!recentlyProved(request)) {
			throw new ReauthenticationRequiredException();
		}
	}

}
