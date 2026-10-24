package com.shift28.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shift28.app.RateLimiter;
import com.shift28.app.RateLimiter.Limit;
import com.shift28.app.TooManyRequestsException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class OAuthStartLimitFilter extends OncePerRequestFilter {

	static final int PER_CLIENT = 20;

	static final int NEW_SESSIONS = 300;

	static final Duration WINDOW = Duration.ofMinutes(15);

	private static final RequestMatcher START = PathPatternRequestMatcher
		.pathPattern("/oauth2/authorization/{registrationId}");

	private final RateLimiter limiter;

	private final ClientIpResolver clientIp;

	private final SessionCleanup cleanup;

	OAuthStartLimitFilter(RateLimiter limiter, ClientIpResolver clientIp, SessionCleanup cleanup) {
		this.limiter = limiter;
		this.clientIp = clientIp;
		this.cleanup = cleanup;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !START.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		boolean newSession = request.getSession(false) == null;
		Limit client = new Limit("oauth-start:ip:" + clientIp.resolve(request), PER_CLIENT, WINDOW);
		try {
			if (newSession) {
				limiter.acquire(client, new Limit(RateLimiter.globalKey("oauth-start"), NEW_SESSIONS, WINDOW));
			}
			else {
				limiter.acquire(client);
			}
		}
		catch (TooManyRequestsException ex) {
			response.sendRedirect(signedIn() ? "/settings?error=rate-limited" : "/login?error=rate-limited");
			return;
		}
		try {
			chain.doFilter(request, response);
		}
		finally {
			if (newSession) {
				cleanup.anonymousSessionStarted();
			}
		}
	}

	private static boolean signedIn() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
	}

}
