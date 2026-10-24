package com.shift28.web;

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

final class ApiClient {

	static final String CSRF_COOKIE = "XSRF-TOKEN";

	static final String CSRF_HEADER = "X-XSRF-TOKEN";

	static final String SESSION_COOKIE = "SESSION";

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final MockMvcTester mvc;

	private final Map<String, Cookie> cookies = new LinkedHashMap<>();

	private final String remoteAddress;

	ApiClient(MockMvcTester mvc) {
		this(mvc, null);
	}

	ApiClient(MockMvcTester mvc, String remoteAddress) {
		this.mvc = mvc;
		this.remoteAddress = remoteAddress;
	}

	ApiClient copy() {
		ApiClient copy = new ApiClient(mvc, remoteAddress);
		copy.cookies.putAll(cookies);
		return copy;
	}

	Cookie cookie(String name) {
		return cookies.get(name);
	}

	MvcTestResult get(String uri) {
		return send(mvc.get().uri(uri));
	}

	MvcTestResult post(String uri, String json) {
		return sendWithCsrf(mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	MvcTestResult post(String uri, String json, String idempotencyKey) {
		return sendWithCsrf(mvc.post()
			.uri(uri)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.header(IDEMPOTENCY_KEY, idempotencyKey));
	}

	MvcTestResult put(String uri, String json) {
		return sendWithCsrf(mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	MvcTestResult delete(String uri) {
		return sendWithCsrf(mvc.delete().uri(uri));
	}

	static <T> T read(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path);
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private MvcTestResult sendWithCsrf(MockMvcRequestBuilder request) {
		if (!cookies.containsKey(CSRF_COOKIE)) {
			get("/api/health");
		}
		return send(request.header(CSRF_HEADER, cookies.get(CSRF_COOKIE).getValue()));
	}

	private MvcTestResult send(MockMvcRequestBuilder request) {
		if (!cookies.isEmpty()) {
			request.cookie(cookies.values().toArray(Cookie[]::new));
		}
		if (remoteAddress != null) {
			request.with(servletRequest -> {
				servletRequest.setRemoteAddr(remoteAddress);
				return servletRequest;
			});
		}
		MvcTestResult result = request.exchange();
		for (Cookie issued : result.getResponse().getCookies()) {
			if (issued.getMaxAge() == 0) {
				cookies.remove(issued.getName());
			}
			else {
				cookies.put(issued.getName(), issued);
			}
		}
		return result;
	}

	static Cookie lastCsrfCookie(MvcTestResult result) {
		Cookie last = null;
		for (Cookie cookie : result.getResponse().getCookies()) {
			if (CSRF_COOKIE.equals(cookie.getName())) {
				last = cookie;
			}
		}
		return last;
	}

}
