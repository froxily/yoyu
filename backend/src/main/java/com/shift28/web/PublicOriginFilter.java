package com.shift28.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

final class PublicOriginFilter extends OncePerRequestFilter {

	private static final Set<String> FORWARDED = Set.of("forwarded", "x-forwarded-host", "x-forwarded-port",
			"x-forwarded-proto", "x-forwarded-prefix", "x-forwarded-ssl", "x-forwarded-for");

	private final PublicOrigin origin;

	PublicOriginFilter(PublicOrigin origin) {
		this.origin = origin;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		chain.doFilter(new PublicOriginRequest(request), new PublicOriginResponse(response));
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	private static boolean forwarded(String name) {
		return FORWARDED.contains(name.toLowerCase(Locale.ROOT));
	}

	private final class PublicOriginRequest extends HttpServletRequestWrapper {

		PublicOriginRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public String getScheme() {
			return "https";
		}

		@Override
		public boolean isSecure() {
			return true;
		}

		@Override
		public String getServerName() {
			return origin.host();
		}

		@Override
		public int getServerPort() {
			return origin.port();
		}

		@Override
		public StringBuffer getRequestURL() {
			return new StringBuffer(origin.url()).append(getRequestURI());
		}

		@Override
		public String getHeader(String name) {
			if ("host".equalsIgnoreCase(name)) {
				return authority();
			}
			return forwarded(name) ? null : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			if ("host".equalsIgnoreCase(name)) {
				return Collections.enumeration(Set.of(authority()));
			}
			return forwarded(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			return Collections.enumeration(
					Collections.list(super.getHeaderNames()).stream().filter(name -> !forwarded(name)).toList());
		}

		@Override
		public long getDateHeader(String name) {
			return forwarded(name) ? -1 : super.getDateHeader(name);
		}

		@Override
		public int getIntHeader(String name) {
			return forwarded(name) ? -1 : super.getIntHeader(name);
		}

		private String authority() {
			return origin.url().substring("https://".length());
		}

	}

	private final class PublicOriginResponse extends HttpServletResponseWrapper {

		PublicOriginResponse(HttpServletResponse response) {
			super(response);
		}

		@Override
		public void sendRedirect(String location) throws IOException {
			super.sendRedirect(onOrigin(location));
		}

		@Override
		public void sendRedirect(String location, int sc) throws IOException {
			super.sendRedirect(onOrigin(location), sc);
		}

		@Override
		public void sendRedirect(String location, boolean clearBuffer) throws IOException {
			super.sendRedirect(onOrigin(location), clearBuffer);
		}

		@Override
		public void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException {
			super.sendRedirect(onOrigin(location), sc, clearBuffer);
		}

		private String onOrigin(String location) {
			return location.startsWith("/") && !location.startsWith("//") ? origin.url() + location : location;
		}

	}

}
