package com.shift28.web;

import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

@Component
class ClientIpResolver {

	private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

	private static final Pattern IPV4 = Pattern
		.compile("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

	private static final Pattern HEX_GROUP = Pattern.compile("[0-9A-Fa-f]{1,4}");

	private static final int IPV6_GROUPS = 8;

	private static final int IPV6_MAX_LENGTH = 45;

	private final String trustedHeader;

	private final AtomicBoolean fallbackLogged = new AtomicBoolean();

	ClientIpResolver(@Value("${client-ip.header:}") String trustedHeader) {
		this.trustedHeader = trustedHeader.strip();
	}

	String resolve(HttpServletRequest request) {
		if (!trustedHeader.isEmpty()) {
			String appended = lastValue(request.getHeaders(trustedHeader));
			if (appended != null && (IPV4.matcher(appended).matches() || isIpv6(appended))) {
				return appended;
			}
			if (fallbackLogged.compareAndSet(false, true)) {
				log.warn("{} was missing or not an IP address; using the socket address for rate limits", trustedHeader);
			}
		}
		ServletRequest original = request;
		while (original instanceof ServletRequestWrapper wrapper) {
			original = wrapper.getRequest();
		}
		return original.getRemoteAddr();
	}

	private static String lastValue(Enumeration<String> values) {
		String last = null;
		while (values != null && values.hasMoreElements()) {
			String value = values.nextElement();
			last = value.substring(value.lastIndexOf(',') + 1).strip();
		}
		return last;
	}

	private static boolean isIpv6(String value) {
		if (value.length() > IPV6_MAX_LENGTH || !value.contains(":")) {
			return false;
		}
		String[] halves = value.split("::", -1);
		if (halves.length > 2) {
			return false;
		}
		int groups = 0;
		for (int half = 0; half < halves.length; half++) {
			if (halves[half].isEmpty()) {
				continue;
			}
			String[] parts = halves[half].split(":", -1);
			for (int i = 0; i < parts.length; i++) {
				boolean lastPart = half == halves.length - 1 && i == parts.length - 1;
				if (lastPart && IPV4.matcher(parts[i]).matches()) {
					groups += 2;
				}
				else if (HEX_GROUP.matcher(parts[i]).matches()) {
					groups++;
				}
				else {
					return false;
				}
			}
		}
		return halves.length == 2 ? groups < IPV6_GROUPS : groups == IPV6_GROUPS;
	}

}
