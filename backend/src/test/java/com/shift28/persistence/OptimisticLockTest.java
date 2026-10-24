package com.shift28.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import com.shift28.IntegrationTest;

@IntegrationTest
class OptimisticLockTest {

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	UserRepository users;

	@Autowired
	JobRepository jobs;

	private UUID job;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE users CASCADE").update();
		UUID user = users.save(new UserEntity("alice@example.com", "hash")).getId();
		job = jobs.save(new JobEntity(user, "Cafe", 1200, 0, "#3b82f6")).getId();
	}

	@Test
	@DisplayName("L4: a row changed after it was loaded fails the flush instead of being overwritten")
	void aConcurrentChangeFailsTheFlush() {
		assertThatExceptionOfType(ObjectOptimisticLockingFailureException.class)
			.isThrownBy(() -> transaction.executeWithoutResult(status -> {
				JobEntity loaded = jobs.findById(job).orElseThrow();
				changeElsewhere();
				loaded.update("Mine", 1300, 0, "#3b82f6");
				jobs.flush();
			}));

		assertThat(jdbc.sql("SELECT name FROM jobs").query(String.class).single()).isEqualTo("Elsewhere");
	}

	private void changeElsewhere() {
		try (Connection other = dataSource.getConnection();
				PreparedStatement update = other
					.prepareStatement("UPDATE jobs SET name = 'Elsewhere', version = version + 1 WHERE id = ?")) {
			other.setAutoCommit(true);
			update.setObject(1, job);
			update.executeUpdate();
		}
		catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

}
