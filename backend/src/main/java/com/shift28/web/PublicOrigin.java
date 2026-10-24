package com.shift28.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PublicOrigin {

	private static final int HTTPS_PORT = 443;

	private final String host;

	private final int port;

	PublicOrigin(@Value("${public-origin:}") String value) {
		if (value.isBlank()) {
			host = null;
			port = -1;
			return;
		}
		URI uri = parse(value.strip());
		host = uri.getHost().toLowerCase(Locale.ROOT);
		port = uri.getPort() == -1 ? HTTPS_PORT : uri.getPort();
	}

	boolean configured() {
		return host != null;
	}

	String url() {
		return "https://" + host + (port == HTTPS_PORT ? "" : ":" + port);
	}

	String host() {
		return host;
	}

	int port() {
		return port;
	}

	private static URI parse(String value) {
		URI uri;
		try {
			uri = new URI(value);
		}
		catch (URISyntaxException ex) {
			throw invalid();
		}
		String path = uri.getRawPath();
		boolean bare = (path == null || path.isEmpty() || path.equals("/")) && uri.getRawQuery() == null
				&& uri.getRawFragment() == null && uri.getRawUserInfo() == null;
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !bare) {
			throw invalid();
		}
		return uri;
	}

	private static IllegalStateException invalid() {
		return new IllegalStateException("PUBLIC_ORIGIN must be a bare https origin such as https://yoyu.azurewebsites.net");
	}

}
