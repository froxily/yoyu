package com.shift28.app;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class AccountRules {

	private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,20}");

	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "support", "help", "system",
			"security", "staff", "official", "moderator", "yoyu", "shift28", "api", "login", "register", "settings",
			"account", "null", "undefined", "me");

	private AccountRules() {
	}

	public static String username(String raw) {
		String username = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
		if (!USERNAME.matcher(username).matches()) {
			throw new FieldValidationException("username", "Use 3 to 20 letters, numbers or underscores.");
		}
		if (RESERVED.contains(username)) {
			throw new FieldValidationException("username", "That username isn't available.");
		}
		return username;
	}

	public static String fullName(String raw) {
		String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
		if (name.isEmpty() || name.codePointCount(0, name.length()) > 100) {
			throw new FieldValidationException("fullName", "Enter your name (up to 100 characters).");
		}
		if (name.codePoints().anyMatch(Character::isISOControl)) {
			throw new FieldValidationException("fullName", "Enter your name without special control characters.");
		}
		return name;
	}

	public static String email(String raw) {
		String email = raw == null ? "" : raw.strip();
		if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
			throw new FieldValidationException("email", "Enter a valid email address.");
		}
		return email;
	}

	public static boolean looksLikeEmail(String identifier) {
		return identifier != null && identifier.contains("@");
	}

}
