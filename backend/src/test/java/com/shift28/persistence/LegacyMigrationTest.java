package com.shift28.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.shift28.IntegrationTest;

@IntegrationTest
class LegacyMigrationTest {

	@Autowired
	PostgreSQLContainer postgres;

	@Autowired
	JdbcClient jdbc;

	@Test
	void legacyUserSurvivesTheAccountMigrationWithNullableProfileAndVerificationFields() {
		String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
		UUID id = UUID.randomUUID();
		String email = "Legacy@Example.com";
		String hash = "$2a$10$legacyPasswordHashPreservedExactly";
		jdbc.sql("CREATE SCHEMA " + schema).update();
		try {
			Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.schemas(schema).defaultSchema(schema).initSql("SET search_path TO " + schema + ", public")
				.locations("classpath:db/migration").target("6").load().migrate();
			jdbc.sql("INSERT INTO " + schema + ".users (id, email, password_hash) VALUES (?, ?, ?)")
				.params(id, email, hash).update();

			Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.schemas(schema).defaultSchema(schema).initSql("SET search_path TO " + schema + ", public")
				.locations("classpath:db/migration").load().migrate();

			Map<String, Object> row = jdbc.sql("""
					SELECT id, email, password_hash, full_name, username, email_verified_at
					FROM %s.users WHERE id = ?""".formatted(schema)).param(id).query().singleRow();
			assertThat(row).containsEntry("id", id).containsEntry("email", email).containsEntry("password_hash", hash)
				.containsEntry("full_name", null).containsEntry("username", null).containsEntry("email_verified_at", null);
			assertThat(jdbc.sql("SELECT count(*) FROM " + schema + ".users").query(Long.class).single()).isOne();
		}
		finally {
			jdbc.sql("DROP SCHEMA " + schema + " CASCADE").update();
		}
	}

}
