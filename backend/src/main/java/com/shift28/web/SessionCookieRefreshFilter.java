package com.shift28.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

final class SessionCookieRefreshFilter extends OncePerRequestFilter {

	private static final Set<String> SKIPPED = Set.of("/api/health", "/api/auth/login", "/api/auth/login/verify",
			"/api/auth/register", "/api/auth/register/verify", "/api/auth/social/complete", "/api/auth/logout");

	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	private final CookieSerializer cookieSerializer;

	SessionCookieRefreshFilter(CookieSerializer cookieSerializer) {
		this.cookieSerializer = cookieSerializer;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/") || SKIPPED.contains(path);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
			HttpSession session = request.getSession(false);
			if (session != null && session.getId().equals(request.getRequestedSessionId())) {
				cookieSerializer.writeCookieValue(new CookieValue(request, response, session.getId()));
			}
		}
		chain.doFilter(request, response);
	}

}
