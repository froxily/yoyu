package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import com.shift28.app.EmailCodeCleanup;

class EmailCodeCleanupApiTest extends ApiTestSupport {

	@Autowired
	EmailCodeCleanup cleanup;

	@Autowired
	ConfigurableApplicationContext context;

	@Autowired
	ScheduledTaskHolder scheduledTasks;

	@Autowired
	DataSource dataSource;

	@Test
	void anIdleAppNeverTouchesTheDatabase() {
		FlakyDataSource unreachable = new FlakyDataSource(dataSource);
		unreachable.failing = true;
		EmailCodeCleanup idle = new EmailCodeCleanup(JdbcClient.create(unreachable), clock);
		insertCode(clock.instant().minus(Duration.ofMinutes(1)));

		idle.sweepWhileCodesMayExpire();

		assertThat(codes()).isOne();
	}

	@Test
	void theSweepRemovesExpiredCodesUntilEveryCodeItWasToldAboutHasExpired() {
		EmailCodeCleanup sweeper = new EmailCodeCleanup(jdbc, clock);
		Instant validUntil = clock.instant().plus(Duration.ofMinutes(10));
		insertCode(validUntil);
		sweeper.codeValidUntil(validUntil);

		sweeper.sweepWhileCodesMayExpire();
		assertThat(codes()).isOne();

		clock.set(validUntil.plusSeconds(1));
		sweeper.sweepWhileCodesMayExpire();
		assertThat(codes()).isZero();

		insertCode(clock.instant().minus(Duration.ofMinutes(1)));
		sweeper.sweepWhileCodesMayExpire();
		assertThat(codes()).isOne();
	}

	@Test
	void sendingACodeStartsTheApplicationsSweep() {
		drainApplicationSweep();
		Instant sent = clock.instant();
		startRegistration(new ApiClient(mvc), "alice@example.com", "alice");

		clock.set(sent.plus(Duration.ofMinutes(11)));
		cleanup.sweepWhileCodesMayExpire();

		assertThat(codes()).isZero();
	}

	@Test
	void resendingACodeKeepsTheApplicationsSweepGoingUntilTheNewExpiry() {
		drainApplicationSweep();
		ApiClient client = new ApiClient(mvc);
		Instant sent = clock.instant();
		String id = startRegistration(client, "alice@example.com", "alice");
		clock.set(sent.plus(Duration.ofMinutes(9)));
		assertThat(client.post("/api/auth/codes/resend", """
				{"verificationId": "%s"}""".formatted(id))).hasStatus(HttpStatus.ACCEPTED);

		clock.set(sent.plus(Duration.ofMinutes(12)));
		cleanup.sweepWhileCodesMayExpire();
		assertThat(codes()).isOne();

		clock.set(sent.plus(Duration.ofMinutes(20)));
		cleanup.sweepWhileCodesMayExpire();
		assertThat(codes()).isZero();
	}

	@Test
	void startupRemovesExpiredCodesAndKeepsSweepingTheOthers() {
		EmailCodeCleanup restarted = new EmailCodeCleanup(jdbc, clock);
		insertCode(clock.instant().minus(Duration.ofHours(8)));
		Instant validUntil = clock.instant().plus(Duration.ofMinutes(5));
		insertCode(validUntil);

		restarted.removeExpiredOnStartup();
		assertThat(codes()).isOne();

		clock.set(validUntil.plusSeconds(1));
		restarted.sweepWhileCodesMayExpire();
		assertThat(codes()).isZero();
	}

	@Test
	void theObligationEndsOnlyAfterASuccessfulSweepPastTheNewestCode() {
		FlakyDataSource flaky = new FlakyDataSource(dataSource);
		EmailCodeCleanup sweeper = new EmailCodeCleanup(JdbcClient.create(flaky), clock);
		Instant validUntil = clock.instant().plus(Duration.ofMinutes(10));
		insertCode(validUntil);
		sweeper.codeValidUntil(validUntil);
		clock.set(validUntil.plusSeconds(1));

		flaky.failing = true;
		assertThatThrownBy(sweeper::sweepWhileCodesMayExpire).isInstanceOf(DataAccessException.class);
		flaky.failing = false;
		sweeper.sweepWhileCodesMayExpire();
		assertThat(codes()).isZero();
	}

	@Test
	void expiredCodesAreRemovedWhenTheApplicationIsReady() {
		insertCode(clock.instant().minus(Duration.ofMinutes(1)));

		context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

		assertThat(codes()).isZero();
	}

	@Test
	void theSweepRunsTenMinutesAfterTheLastOneEnded() {
		assertThat(scheduledTasks.getScheduledTasks()).anySatisfy(task -> {
			assertThat(task.getTask().toString()).contains("EmailCodeCleanup.sweepWhileCodesMayExpire");
			assertThat(task.getTask()).isInstanceOfSatisfying(FixedDelayTask.class, delay -> {
				assertThat(delay.getIntervalDuration()).isEqualTo(Duration.ofMinutes(10));
				assertThat(delay.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(10));
			});
		});
	}

	private void drainApplicationSweep() {
		Instant now = clock.instant();
		clock.set(now.plus(Duration.ofDays(3650)));
		cleanup.sweepWhileCodesMayExpire();
		clock.set(now);
	}

	private void insertCode(Instant expiresAt) {
		jdbc.sql("INSERT INTO email_codes (id, purpose, last_sent_at, expires_at) VALUES (?, 'RESET_PASSWORD', ?, ?)")
			.params(UUID.randomUUID(), Timestamp.from(expiresAt.minus(Duration.ofMinutes(10))), Timestamp.from(expiresAt))
			.update();
	}

	private int codes() {
		return jdbc.sql("SELECT count(*) FROM email_codes").query(Integer.class).single();
	}

	private static final class FlakyDataSource extends DelegatingDataSource {

		volatile boolean failing;

		FlakyDataSource(DataSource target) {
			super(target);
		}

		@Override
		public Connection getConnection() throws SQLException {
			if (failing) {
				throw new SQLException("database unavailable");
			}
			return super.getConnection();
		}

	}

}
