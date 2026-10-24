package com.shift28.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.shift28.IntegrationTest;

@IntegrationTest
class UserLostUpdateTest {

	@Autowired
	UserRepository users;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate transactions;

	@Test
	void savingAProfileNeverWritesBackAStalePasswordHashOrEmail() {
		jdbc.sql("TRUNCATE users CASCADE").update();
		UUID id = users.save(new UserEntity("alice@example.com", "old-hash")).getId();

		transactions.executeWithoutResult(status -> {
			UserEntity loaded = users.findById(id).orElseThrow();
			jdbc.sql("UPDATE users SET password_hash = 'new-hash', email = 'alice.new@example.com', "
					+ "session_version = session_version + 1 WHERE id = ?")
				.param(id)
				.update();
			loaded.changeProfile("Alice", "alice");
			users.flush();
		});

		assertThat(jdbc.sql("SELECT password_hash, email, session_version, username FROM users WHERE id = ?")
			.param(id)
			.query()
			.singleRow()).containsEntry("password_hash", "new-hash")
			.containsEntry("email", "alice.new@example.com")
			.containsEntry("session_version", 1L)
			.containsEntry("username", "alice");
	}

}
