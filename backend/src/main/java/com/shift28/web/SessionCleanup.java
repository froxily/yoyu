package com.shift28.web;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.session.autoconfigure.SessionProperties;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
class SessionCleanup {

	static final Duration ANONYMOUS_MAX_AGE = Duration.ofMinutes(30);

	private final JdbcClient jdbc;

	private final Duration anonymousIdle;

	private final Clock clock;

	private boolean pending;

	private long pendingUntil;

	@Autowired
	SessionCleanup(JdbcClient jdbc, SessionProperties session) {
		this(jdbc, session, Clock.systemUTC());
	}

	SessionCleanup(JdbcClient jdbc, SessionProperties session, Clock clock) {
		this.jdbc = jdbc;
		this.anonymousIdle = session.getTimeout();
		this.clock = clock;
	}

	void anonymousSessionStarted() {
		require(clock.millis() + ANONYMOUS_MAX_AGE.toMillis());
	}

	@EventListener(ApplicationReadyEvent.class)
	void removeStaleOnStartup() {
		removeStale(clock.millis());
		long newest = jdbc.sql("SELECT coalesce(max(creation_time), 0) FROM spring_session WHERE principal_name IS NULL")
			.query(Long.class)
			.single();
		if (newest > 0) {
			require(newest + ANONYMOUS_MAX_AGE.toMillis());
		}
	}

	@Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
	void sweepWhileAnonymousSessionsMayExist() {
		synchronized (this) {
			if (!pending) {
				return;
			}
		}
		long now = clock.millis();
		removeStale(now);
		synchronized (this) {
			if (now > pendingUntil) {
				pending = false;
			}
		}
	}

	private synchronized void require(long until) {
		pending = true;
		pendingUntil = Math.max(pendingUntil, until);
	}

	private void removeStale(long now) {
		jdbc.sql("""
				DELETE FROM spring_session s
				WHERE s.expiry_time < :now
				   OR (s.principal_name IS NULL
				       AND (s.last_access_time < :idle OR s.creation_time < :age)
				       AND NOT EXISTS (SELECT 1 FROM spring_session_attributes a
				                       WHERE a.session_primary_id = s.primary_id AND a.attribute_name = :context))""")
			.param("now", now)
			.param("idle", now - anonymousIdle.toMillis())
			.param("age", now - ANONYMOUS_MAX_AGE.toMillis())
			.param("context", HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
			.update();
	}

}
