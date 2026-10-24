package com.shift28.app;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailCodeService {

	static final Duration TTL = Duration.ofMinutes(10);

	static final Duration COOLDOWN = Duration.ofSeconds(60);

	static final int MAX_ATTEMPTS = 5;

	static final int MAX_SENDS = 5;

	private static final Pattern CODE = Pattern.compile("\\d{6}");

	private static final String COLUMNS = "id, purpose, email, user_id, full_name, username, password_hash, previous_email";

	private final SecureRandom random = new SecureRandom();

	private final JdbcClient jdbc;

	private final Clock clock;

	private final SecretKeySpec key;

	private final EmailCodeCleanup cleanup;

	EmailCodeService(JdbcClient jdbc, Clock clock, @Value("${otp.secret:}") String secret,
			@Value("${mail.enabled:false}") boolean mailEnabled, EmailCodeCleanup cleanup) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.key = new SecretKeySpec(keyBytes(secret, mailEnabled), "HmacSHA256");
		this.cleanup = cleanup;
	}

	private byte[] keyBytes(String secret, boolean mailEnabled) {
		if (secret == null || secret.isBlank()) {
			if (mailEnabled) {
				throw new IllegalStateException("OTP_SECRET must be set when mail is enabled");
			}
			byte[] ephemeral = new byte[32];
			random.nextBytes(ephemeral);
			return ephemeral;
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("OTP_SECRET must be at least 32 bytes");
		}
		return bytes;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Issued issue(CodePurpose purpose, String email, UUID userId, Pending pending, String previousEmail,
			boolean withCode) {
		UUID id = UUID.randomUUID();
		String code = withCode ? newCode() : null;
		Instant now = clock.instant();
		jdbc.sql("""
				INSERT INTO email_codes (id, purpose, email, user_id, full_name, username, password_hash,
				  previous_email, code_hash, last_sent_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
			.params(id, purpose.name(), email, userId, pending == null ? null : pending.fullName(),
					pending == null ? null : pending.username(), pending == null ? null : pending.passwordHash(),
					previousEmail, code == null ? null : hash(id, purpose, code), Timestamp.from(now),
					Timestamp.from(now.plus(TTL)))
			.update();
		cleanup.codeValidUntil(now.plus(TTL));
		return new Issued(id, code);
	}

	@Transactional
	public Resent resend(UUID id, UUID currentUserId) {
		Locked locked = lock(id).orElseThrow(InvalidCodeException::new);
		Instant now = clock.instant();
		if (!locked.expiresAt().isAfter(now) || !belongsTo(locked.row(), currentUserId)) {
			throw new InvalidCodeException();
		}
		Instant allowedAt = locked.lastSentAt().plus(COOLDOWN);
		if (now.isBefore(allowedAt)) {
			throw new TooManyRequestsException(Math.max(1, Duration.between(now, allowedAt).toSeconds()));
		}
		if (locked.sends() >= MAX_SENDS) {
			throw new TooManyRequestsException(Math.max(1, Duration.between(now, locked.expiresAt()).toSeconds()));
		}
		CodeRow row = locked.row();
		String code = locked.hasCode() ? newCode() : null;
		jdbc.sql("""
				UPDATE email_codes SET code_hash = ?, attempts = 0, sends = sends + 1, last_sent_at = ?, expires_at = ?
				WHERE id = ?""")
			.params(code == null ? null : hash(id, row.purpose(), code), Timestamp.from(now),
					Timestamp.from(now.plus(TTL)), id)
			.update();
		cleanup.codeValidUntil(now.plus(TTL));
		return new Resent(row, code);
	}

	@Transactional(noRollbackFor = InvalidCodeException.class)
	public CodeRow check(UUID id, CodePurpose purpose, String code, UUID currentUserId) {
		Locked locked = lock(id).filter(found -> found.row().purpose() == purpose)
			.orElseThrow(InvalidCodeException::new);
		CodeRow row = locked.row();
		if (!locked.expiresAt().isAfter(clock.instant()) || !locked.hasCode() || !belongsTo(row, currentUserId)
				|| locked.attempts() >= MAX_ATTEMPTS) {
			throw new InvalidCodeException();
		}
		String normalized = code == null ? "" : code.replaceAll("\\s", "");
		if (!CODE.matcher(normalized).matches()
				|| !MessageDigest.isEqual(locked.codeHash(), hash(id, purpose, normalized))) {
			jdbc.sql("UPDATE email_codes SET attempts = attempts + 1 WHERE id = ?").param(id).update();
			throw new InvalidCodeException();
		}
		return row;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void consume(UUID id) {
		if (jdbc.sql("DELETE FROM email_codes WHERE id = ?").param(id).update() != 1) {
			throw new InvalidCodeException();
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteFor(UUID userId, CodePurpose purpose) {
		jdbc.sql("DELETE FROM email_codes WHERE user_id = ? AND purpose = ?").params(userId, purpose.name()).update();
	}

	private static boolean belongsTo(CodeRow row, UUID currentUserId) {
		boolean signedInFlow = row.purpose() == CodePurpose.CHANGE_EMAIL || row.purpose() == CodePurpose.REAUTHENTICATE;
		return !signedInFlow || (currentUserId != null && currentUserId.equals(row.userId()));
	}

	private Optional<Locked> lock(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS
				+ ", code_hash, attempts, sends, last_sent_at, expires_at FROM email_codes WHERE id = ? FOR UPDATE")
			.param(id)
			.query((rs, rowNum) -> new Locked(row(rs), rs.getBytes("code_hash"), rs.getInt("attempts"),
					rs.getInt("sends"), rs.getTimestamp("last_sent_at").toInstant(),
					rs.getTimestamp("expires_at").toInstant()))
			.optional();
	}

	private static CodeRow row(ResultSet rs) throws SQLException {
		return new CodeRow(rs.getObject("id", UUID.class), CodePurpose.valueOf(rs.getString("purpose")),
				rs.getString("email"), rs.getObject("user_id", UUID.class), rs.getString("full_name"),
				rs.getString("username"), rs.getString("password_hash"), rs.getString("previous_email"));
	}

	private String newCode() {
		return "%06d".formatted(random.nextInt(1_000_000));
	}

	private byte[] hash(UUID id, CodePurpose purpose, String code) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return mac.doFinal((id + ":" + purpose.name() + ":" + code).getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	public record Pending(String fullName, String username, String passwordHash) {
	}

	public record Issued(UUID id, String code) {

		@Override
		public String toString() {
			return "Issued[id=" + id + "]";
		}

	}

	public record Resent(CodeRow row, String code) {

		@Override
		public String toString() {
			return "Resent[id=" + row.id() + "]";
		}

	}

	public record CodeRow(UUID id, CodePurpose purpose, String email, UUID userId, String fullName, String username,
			String passwordHash, String previousEmail) {

		@Override
		public String toString() {
			return "CodeRow[id=" + id + ", purpose=" + purpose + "]";
		}

	}

	private record Locked(CodeRow row, byte[] codeHash, int attempts, int sends, Instant lastSentAt,
			Instant expiresAt) {

		boolean hasCode() {
			return codeHash != null;
		}

	}

}
