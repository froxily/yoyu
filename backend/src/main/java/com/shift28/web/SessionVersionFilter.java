package com.shift28.web;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.shift28.app.AccountService;
import com.shift28.app.AccountUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

final class SessionVersionFilter extends OncePerRequestFilter {

	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	private final AccountService accounts;

	SessionVersionFilter(AccountService accounts) {
		this.accounts = accounts;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
		return !path.startsWith("/api/") || path.equals("/api/health");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (trustResolver.isAuthenticated(authentication) && !current(authentication)) {
			SecurityContextHolder.clearContext();
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
		}
		chain.doFilter(request, response);
	}

	private boolean current(Authentication authentication) {
		if (!(authentication.getPrincipal() instanceof AccountUser user)) {
			return false;
		}
		Optional<Long> version = accounts.sessionVersion(user.id());
		return version.isPresent() && version.get() == user.sessionVersion();
	}

}
