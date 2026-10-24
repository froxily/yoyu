package com.shift28.web;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

final class OAuthSession {

	static final Duration LIFETIME = Duration.ofMinutes(10);

	private static final String INTENT = OAuthSession.class.getName() + ".intent";

	private static final String PENDING = OAuthSession.class.getName() + ".pending";

	private OAuthSession() {
	}

	static void saveIntent(HttpServletRequest request, Intent intent) {
		request.getSession().setAttribute(INTENT, intent);
	}

	static Intent peekIntent(HttpServletRequest request, Clock clock) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(INTENT) instanceof Intent intent)) {
			return null;
		}
		return intent.expiresAt().isAfter(clock.instant()) ? intent : null;
	}

	static Intent takeIntent(HttpServletRequest request, Clock clock) {
		Intent intent = peekIntent(request, clock);
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.removeAttribute(INTENT);
		}
		return intent;
	}

	static void savePending(HttpServletRequest request, PendingSignUp pending) {
		request.getSession().setAttribute(PENDING, pending);
	}

	static PendingSignUp pending(HttpServletRequest request, Clock clock) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(PENDING) instanceof PendingSignUp pending)) {
			return null;
		}
		if (!pending.expiresAt().isAfter(clock.instant())) {
			session.removeAttribute(PENDING);
			return null;
		}
		return pending;
	}

	static void clearPending(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.removeAttribute(PENDING);
		}
	}

	enum Kind {

		LINK, REAUTHENTICATE

	}

	record Intent(Kind kind, String provider, UUID userId, Instant expiresAt) implements Serializable {
	}

	record PendingSignUp(String provider, String subject, String suggestedName, String verifiedEmail,
			String unverifiedEmail, boolean fresh, Instant expiresAt) implements Serializable {
	}

}
