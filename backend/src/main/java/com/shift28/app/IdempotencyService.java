package com.shift28.app;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

	private final JdbcClient jdbc;

	IdempotencyService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<StoredResponse> claim(UUID userId, String key, byte[] requestHash) {
		boolean claimed = jdbc.sql("""
				INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, expires_at)
				VALUES (?, ?, ?, now() + interval '24 hours')
				ON CONFLICT (user_id, idempotency_key) DO UPDATE
				  SET request_hash = EXCLUDED.request_hash, response_status = NULL, response_body = NULL,
				      created_at = now(), expires_at = EXCLUDED.expires_at
				  WHERE idempotency_keys.expires_at <= now()
				RETURNING true""").params(userId, key, requestHash).query(Boolean.class).optional().isPresent();
		if (claimed) {
			return Optional.empty();
		}
		Stored stored = jdbc.sql("""
				SELECT request_hash, response_status, response_body FROM idempotency_keys
				WHERE user_id = ? AND idempotency_key = ?""")
			.params(userId, key)
			.query((rs, row) -> new Stored(rs.getBytes(1), rs.getInt(2), rs.getString(3)))
			.single();
		if (!Arrays.equals(stored.requestHash(), requestHash)) {
			throw new IdempotencyKeyReusedException();
		}
		return Optional.of(new StoredResponse(stored.status(), stored.body()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void complete(UUID userId, String key, int status, String body) {
		jdbc.sql("""
				UPDATE idempotency_keys SET response_status = ?, response_body = ?
				WHERE user_id = ? AND idempotency_key = ?""").params(status, body, userId, key).update();
	}

	@Transactional
	public void deleteAll(UUID userId) {
		jdbc.sql("DELETE FROM idempotency_keys WHERE user_id = ?").param(userId).update();
	}

	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Tokyo")
	@Transactional
	public int removeExpired() {
		return jdbc.sql("DELETE FROM idempotency_keys WHERE expires_at <= now()").update();
	}

	public record StoredResponse(int status, String body) {
	}

	private record Stored(byte[] requestHash, int status, String body) {
	}

}
