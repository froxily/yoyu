package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class WriteSerializationApiTest extends ApiTestSupport {

	private static final Duration WAIT = Duration.ofSeconds(15);

	@Autowired
	DataSource dataSource;

	@Test
	@DisplayName("L6: a shift write waits for the user's lock, then checks against what was committed meanwhile")
	void shiftWritesAreSerializedPerUser() throws Exception {
		clock.set(jst("2026-11-01T09:00"));
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-03", "10:00", "17:00");
		UUID aliceId = UUID.fromString(ApiClient.read(alice.get("/api/auth/me"), "$.id"));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Connection holder = dataSource.getConnection()) {
			holder.setAutoCommit(false);
			lockUser(holder, aliceId);

			Future<MvcTestResult> draft = executor
				.submit(() -> alice.copy().post("/api/shifts", shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00")));
			awaitWaitingBackends(1);
			assertThat(draft).isNotDone();

			try (PreparedStatement insert = holder.prepareStatement("""
					INSERT INTO shifts (id, user_id, job_id, start_at, end_at)
					VALUES (gen_random_uuid(), ?, ?, '2026-11-04T10:00:00+09:00', '2026-11-04T17:00:00+09:00')""")) {
				insert.setObject(1, aliceId);
				insert.setObject(2, UUID.fromString(cafe));
				insert.executeUpdate();
			}
			holder.commit();

			assertThat(draft.get(WAIT.toSeconds(), TimeUnit.SECONDS)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.bodyJson()
				.extractingPath("$.compliance.status")
				.isEqualTo("VIOLATION");
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(count("shifts")).isEqualTo(3);
	}

	@Test
	@DisplayName("Two concurrent idempotent creates from one user take the key row, then the user row, without deadlock")
	void concurrentWritesWithDifferentKeysDoNotDeadlock() throws Exception {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		UUID aliceId = UUID.fromString(ApiClient.read(alice.get("/api/auth/me"), "$.id"));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try (Connection holder = dataSource.getConnection()) {
			holder.setAutoCommit(false);
			lockUser(holder, aliceId);

			List<Future<MvcTestResult>> writes = List.of(
					executor.submit(() -> alice.copy()
						.post("/api/shifts", shift(cafe, "2026-11-02T10:00", "2026-11-02T12:00"), "first-key")),
					executor.submit(() -> alice.copy()
						.post("/api/shifts", shift(cafe, "2026-11-03T10:00", "2026-11-03T12:00"), "second-key")));
			awaitWaitingBackends(2);
			assertThat(count("idempotency_keys")).isZero();
			holder.commit();

			for (Future<MvcTestResult> write : writes) {
				assertThat(write.get(WAIT.toSeconds(), TimeUnit.SECONDS)).hasStatus(HttpStatus.CREATED);
			}
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(count("shifts")).isEqualTo(2);
		assertThat(count("idempotency_keys")).isEqualTo(2);
	}

	private static void lockUser(Connection connection, UUID userId) throws Exception {
		try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE")) {
			lock.setObject(1, userId);
			lock.executeQuery().close();
		}
	}

	private void awaitWaitingBackends(int expected) throws InterruptedException {
		Instant deadline = Instant.now().plus(WAIT);
		while (waitingBackends() < expected) {
			assertThat(Instant.now()).as("requests waiting on a lock").isBefore(deadline);
			Thread.sleep(20);
		}
	}

	private long waitingBackends() {
		return jdbc.sql("""
				SELECT count(*) FROM pg_stat_activity
				WHERE datname = current_database() AND wait_event_type = 'Lock' AND state = 'active'""")
			.query(Long.class)
			.single();
	}

}
