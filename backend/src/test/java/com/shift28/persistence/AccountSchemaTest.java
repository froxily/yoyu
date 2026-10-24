package com.shift28.persistence;

import static com.shift28.persistence.SqlStates.CHECK;
import static com.shift28.persistence.SqlStates.UNIQUE;
import static com.shift28.persistence.SqlStates.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shift28.IntegrationTest;

@IntegrationTest
class AccountSchemaTest {

	@Autowired
	JdbcClient jdbc;

	private UUID user;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE users, email_codes CASCADE").update();
		user = insertUser("alice@example.com", "alice");
	}

	@Test
	void usernameCheckRejectsUppercase() {
		assertRejected(CHECK, () -> setUsername("Alice"));
	}

	@Test
	void usernameCheckRejectsFewerThanThreeCharacters() {
		assertRejected(CHECK, () -> setUsername("ab"));
		assertThatNoException().isThrownBy(() -> setUsername("abc"));
	}

	@Test
	void usernameCheckRejectsMoreThanTwentyCharacters() {
		assertRejected(CHECK, () -> setUsername("a".repeat(21)));
		assertThatNoException().isThrownBy(() -> setUsername("a".repeat(20)));
	}

	@Test
	void usernameCheckRejectsInvalidCharacters() {
		for (String username : new String[] { "has space", "dot.ted", "has-dash", "café" }) {
			assertRejected(CHECK, () -> setUsername(username));
		}
		assertThatNoException().isThrownBy(() -> setUsername("valid_123"));
	}

	@Test
	void usernamesAreUnique() {
		assertRejected(UNIQUE, () -> insertUser("bob@example.com", "alice"));
		assertThatNoException().isThrownBy(() -> insertUser("bob@example.com", "bob"));
	}

	@Test
	void emailsRemainUniqueIgnoringCase() {
		assertRejected(UNIQUE, () -> insertUser("ALICE@EXAMPLE.COM", "bob"));
		assertThatNoException().isThrownBy(() -> insertUser("bob@example.com", "bob"));
	}

	@Test
	void passwordHashAndEmailCanBothBeNull() {
		UUID id = UUID.randomUUID();

		assertThatNoException().isThrownBy(() -> jdbc.sql("INSERT INTO users (id) VALUES (?)").param(id).update());
		assertThat(jdbc.sql("SELECT password_hash IS NULL AND email IS NULL FROM users WHERE id = ?").param(id)
			.query(Boolean.class).single()).isTrue();
	}

	@Test
	void passwordHashRequiresAnEmailAddress() {
		assertRejected(CHECK, () -> jdbc.sql("INSERT INTO users (id, password_hash) VALUES (?, 'hash')")
			.param(UUID.randomUUID()).update());
		assertThatNoException().isThrownBy(() -> jdbc.sql("UPDATE users SET password_hash = 'hash' WHERE id = ?")
			.param(user).update());
	}

	@Test
	void fullNameCheckRejectsEmptyAndOverlongNames() {
		for (String name : new String[] { "", "a".repeat(101) }) {
			assertRejected(CHECK, () -> jdbc.sql("UPDATE users SET full_name = ? WHERE id = ?").params(name, user).update());
		}
		for (String name : new String[] { "a", "a".repeat(100) }) {
			assertThatNoException().isThrownBy(() -> jdbc.sql("UPDATE users SET full_name = ? WHERE id = ?")
				.params(name, user).update());
		}
	}

	@Test
	void providerAndSubjectAreUniqueAcrossUsers() {
		UUID other = insertUser("bob@example.com", "bob");
		insertIdentity(user, "google", "same-subject");

		assertRejected(UNIQUE, () -> insertIdentity(other, "google", "same-subject"));
		assertThatNoException().isThrownBy(() -> insertIdentity(other, "line", "same-subject"));
	}

	@Test
	void eachUserCanOnlyHaveOneIdentityPerProvider() {
		insertIdentity(user, "google", "first-subject");

		assertRejected(UNIQUE, () -> insertIdentity(user, "google", "second-subject"));
		assertThatNoException().isThrownBy(() -> insertIdentity(user, "line", "second-subject"));
	}

	@Test
	void identityProviderCheckRejectsUnsupportedProviders() {
		assertRejected(CHECK, () -> insertIdentity(user, "github", "subject"));
	}

	@Test
	void deletingAUserCascadesToTheirIdentitiesOnly() {
		UUID other = insertUser("bob@example.com", "bob");
		insertIdentity(user, "google", "alice-google");
		insertIdentity(user, "line", "alice-line");
		insertIdentity(other, "google", "bob-google");

		jdbc.sql("DELETE FROM users WHERE id = ?").param(user).update();

		assertThat(jdbc.sql("SELECT user_id FROM user_identities").query(UUID.class).list()).containsExactly(other);
	}

	@Test
	void emailCodePurposeCheckAcceptsOnlyTheFiveSupportedPurposes() {
		assertRejected(CHECK, () -> insertCode(user, "LOGIN"));
		for (String purpose : new String[] { "REGISTER", "VERIFY_EXISTING", "RESET_PASSWORD", "CHANGE_EMAIL", "REAUTHENTICATE" }) {
			assertThatNoException().isThrownBy(() -> insertCode(user, purpose));
		}
	}

	@Test
	void deletingAUserCascadesToTheirEmailCodesOnly() {
		UUID other = insertUser("bob@example.com", "bob");
		insertCode(user, "CHANGE_EMAIL");
		insertCode(user, "REAUTHENTICATE");
		insertCode(other, "RESET_PASSWORD");

		jdbc.sql("DELETE FROM users WHERE id = ?").param(user).update();

		assertThat(jdbc.sql("SELECT user_id FROM email_codes").query(UUID.class).list()).containsExactly(other);
	}

	private UUID insertUser(String email, String username) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO users (id, email, username) VALUES (?, ?, ?)").params(id, email, username).update();
		return id;
	}

	private void setUsername(String username) {
		jdbc.sql("UPDATE users SET username = ? WHERE id = ?").params(username, user).update();
	}

	private void insertIdentity(UUID userId, String provider, String subject) {
		jdbc.sql("INSERT INTO user_identities (id, user_id, provider, subject) VALUES (?, ?, ?, ?)")
			.params(UUID.randomUUID(), userId, provider, subject).update();
	}

	private void insertCode(UUID userId, String purpose) {
		jdbc.sql("""
				INSERT INTO email_codes (id, user_id, purpose, last_sent_at, expires_at)
				VALUES (?, ?, ?, now(), now() + interval '10 minutes')""")
			.params(UUID.randomUUID(), userId, purpose).update();
	}

}
