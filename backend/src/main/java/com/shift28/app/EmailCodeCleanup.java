package com.shift28.app;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailCodeCleanup {

	private final JdbcClient jdbc;

	private final Clock clock;

	private boolean pending;

	private Instant pendingUntil = Instant.MIN;

	public EmailCodeCleanup(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public synchronized void codeValidUntil(Instant expiresAt) {
		pending = true;
		if (expiresAt.isAfter(pendingUntil)) {
			pendingUntil = expiresAt;
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void removeExpiredOnStartup() {
		removeExpired(clock.instant());
		jdbc.sql("SELECT max(expires_at) FROM email_codes")
			.query(Timestamp.class)
			.optional()
			.ifPresent(newest -> codeValidUntil(newest.toInstant()));
	}

	@Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
	public void sweepWhileCodesMayExpire() {
		synchronized (this) {
			if (!pending) {
				return;
			}
		}
		Instant now = clock.instant();
		removeExpired(now);
		synchronized (this) {
			if (now.isAfter(pendingUntil)) {
				pending = false;
				pendingUntil = Instant.MIN;
			}
		}
	}

	private void removeExpired(Instant now) {
		jdbc.sql("DELETE FROM email_codes WHERE expires_at <= ?").param(Timestamp.from(now)).update();
	}

}
