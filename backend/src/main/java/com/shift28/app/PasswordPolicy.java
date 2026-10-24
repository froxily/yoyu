package com.shift28.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 10;

	public static final int MAX_BYTES = 72;

	private final Set<String> common;

	PasswordPolicy() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new ClassPathResource("common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
			this.common = reader.lines().map(String::strip).filter(line -> !line.isEmpty()).collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void check(String password, String... personal) {
		if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
			throw invalid("Use at least 10 characters.");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw invalid("Use at most 72 bytes.");
		}
		String lower = password.toLowerCase(Locale.ROOT);
		if (common.contains(lower)) {
			throw invalid("This password is too common. Choose another.");
		}
		for (String value : personal) {
			if (value != null && !value.isBlank() && lower.equals(value.strip().toLowerCase(Locale.ROOT))) {
				throw invalid("Don't use your email or username as your password.");
			}
		}
	}

	private static FieldValidationException invalid(String message) {
		return new FieldValidationException("password", message);
	}

}
