package com.shift28.persistence;

import static com.shift28.persistence.SqlStates.CHECK;
import static com.shift28.persistence.SqlStates.EXCLUSION;
import static com.shift28.persistence.SqlStates.FOREIGN_KEY;
import static com.shift28.persistence.SqlStates.NOT_NULL;
import static com.shift28.persistence.SqlStates.UNIQUE;
import static com.shift28.persistence.SqlStates.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shift28.IntegrationTest;

@IntegrationTest
class SchemaConstraintsTest {

	private static final ZoneOffset JST = ZoneOffset.ofHours(9);

	@Autowired
	JdbcClient jdbc;

	private UUID user;

	private UUID job;

	private int shiftDay;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE users CASCADE").update();
		user = insertUser("student@example.com");
		job = insertJob(user);
	}

	@ParameterizedTest(name = "{0}.{1}")
	@CsvSource({ "users, id", "users, created_at", "jobs, id", "jobs, user_id",
			"jobs, name", "jobs, hourly_wage_yen", "jobs, transport_yen", "jobs, color", "jobs, archived",
			"jobs, created_at", "shifts, id", "shifts, user_id", "shifts, job_id", "shifts, start_at", "shifts, end_at",
			"shifts, created_at", "shifts, updated_at", "vacation_periods, id", "vacation_periods, user_id",
			"vacation_periods, start_date", "vacation_periods, end_date" })
	void requiredColumnsRejectNull(String table, String column) {
		Map<String, Object> row = validRow(table);
		row.put(column, null);

		assertRejected(NOT_NULL, () -> insert(table, row));
	}

	@ParameterizedTest
	@ValueSource(strings = { "users", "jobs", "shifts", "vacation_periods" })
	void primaryKeysAreUnique(String table) {
		Map<String, Object> first = validRow(table);
		Map<String, Object> second = validRow(table);
		second.put("id", first.get("id"));
		insert(table, first);

		assertRejected(UNIQUE, () -> insert(table, second));
	}

	@Test
	void optionalColumnsAcceptNull() {
		assertThatNoException().isThrownBy(() -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T12:00"));
		assertThat(jdbc.sql("SELECT count(*) FROM shifts WHERE break_start IS NULL AND break_end IS NULL AND note IS NULL")
			.query(Long.class)
			.single()).isEqualTo(1);
		assertThatNoException().isThrownBy(() -> insert("vacation_periods", validRow("vacation_periods")));
	}

	@Test
	void defaultsFillOmittedColumns() {
		insertShift(user, job, "2026-11-02T10:00", "2026-11-02T12:00");

		Map<String, Object> jobRow = jdbc.sql("SELECT transport_yen, color, archived, created_at FROM jobs WHERE id = ?")
			.param(job)
			.query()
			.singleRow();
		assertThat(jobRow).containsEntry("transport_yen", 0)
			.containsEntry("color", "#3b82f6")
			.containsEntry("archived", false)
			.extractingByKey("created_at")
			.isNotNull();
		assertThat(jdbc.sql("SELECT count(*) FROM users WHERE created_at IS NOT NULL").query(Long.class).single())
			.isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM shifts WHERE created_at IS NOT NULL AND updated_at IS NOT NULL")
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void emailsAreUniqueIgnoringCase() {
		insertUser("Alice@Example.com");

		assertRejected(UNIQUE, () -> insertUser("alice@example.COM"));
		assertThatNoException().isThrownBy(() -> insertUser("alice2@example.com"));
	}

	@Test
	void hourlyWageMustBePositive() {
		assertRejected(CHECK, () -> insertJob(user, 0, 0));
		assertRejected(CHECK, () -> insertJob(user, -1, 0));
		assertThatNoException().isThrownBy(() -> insertJob(user, 1, 0));
	}

	@Test
	void transportMustNotBeNegative() {
		assertRejected(CHECK, () -> insertJob(user, 1200, -1));
		assertThatNoException().isThrownBy(() -> insertJob(user, 1200, 0));
	}

	@Test
	@DisplayName("T16: an overlapping shift on any job is rejected; a shift that only touches is accepted")
	void t16OverlapIsRejectedAndTouchingShiftsAreAllowed() {
		UUID otherJob = insertJob(user);
		insertShift(user, job, "2026-11-02T10:00", "2026-11-02T14:00");

		assertRejected(EXCLUSION, () -> insertShift(user, otherJob, "2026-11-02T13:00", "2026-11-02T15:00"));
		assertRejected(EXCLUSION, () -> insertShift(user, job, "2026-11-02T13:00", "2026-11-02T15:00"));
		assertThatNoException().isThrownBy(() -> insertShift(user, otherJob, "2026-11-02T14:00", "2026-11-02T16:00"));
		assertThatNoException().isThrownBy(() -> insertShift(user, job, "2026-11-02T08:00", "2026-11-02T10:00"));
	}

	@Test
	void overlapIsCheckedOnUpdate() {
		insertShift(user, job, "2026-11-02T10:00", "2026-11-02T14:00");
		UUID later = insertShift(user, job, "2026-11-02T15:00", "2026-11-02T17:00");

		assertRejected(EXCLUSION, () -> jdbc.sql("UPDATE shifts SET start_at = :start WHERE id = :id")
			.param("start", jst("2026-11-02T13:59"))
			.param("id", later)
			.update());
	}

	@Test
	void differentUsersMayHaveOverlappingShifts() {
		UUID other = insertUser("other@example.com");
		insertShift(user, job, "2026-11-02T10:00", "2026-11-02T14:00");

		assertThatNoException()
			.isThrownBy(() -> insertShift(other, insertJob(other), "2026-11-02T10:00", "2026-11-02T14:00"));
	}

	@Test
	@DisplayName("T20: end before start, a shift of 24 h 1 min and a break outside the shift fail a CHECK")
	void t20InvalidShiftsAreRejected() {
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T09:00"));
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-03T10:01"));
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T18:00", "2026-11-02T09:00",
				"2026-11-02T11:00"));
	}

	@Test
	void shiftMustEndAfterItStarts() {
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T10:00"));
	}

	@Test
	void shiftOfExactly24HoursIsAllowed() {
		assertThatNoException().isThrownBy(() -> insertShift(user, job, "2026-11-02T10:00", "2026-11-03T10:00"));
	}

	@Test
	void breakMustBeCompleteNonEmptyAndInsideTheShift() {
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T18:00", "2026-11-02T13:00", null));
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T18:00", null, "2026-11-02T14:00"));
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T18:00", "2026-11-02T17:00",
				"2026-11-02T18:01"));
		assertRejected(CHECK, () -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T18:00", "2026-11-02T13:00",
				"2026-11-02T13:00"));
		assertThatNoException().isThrownBy(() -> insertShift(user, job, "2026-11-02T10:00", "2026-11-02T18:00",
				"2026-11-02T10:00", "2026-11-02T18:00"));
	}

	@Test
	void vacationMustNotEndBeforeItStarts() {
		assertRejected(CHECK, () -> insertVacation(user, "2026-12-24", "2026-12-23"));
		assertThatNoException().isThrownBy(() -> insertVacation(user, "2026-12-24", "2026-12-24"));
	}

	@Test
	void referencesMustExist() {
		UUID missing = UUID.randomUUID();

		assertRejected(FOREIGN_KEY, () -> insertJob(missing));
		assertRejected(FOREIGN_KEY, () -> insertShift(missing, job, "2026-11-02T10:00", "2026-11-02T12:00"));
		assertRejected(FOREIGN_KEY, () -> insertShift(user, missing, "2026-11-02T10:00", "2026-11-02T12:00"));
		assertRejected(FOREIGN_KEY, () -> insertVacation(missing, "2026-12-21", "2027-01-06"));
	}

	@Test
	void shiftCannotReferenceAnotherUsersJob() {
		UUID other = insertUser("other@example.com");
		UUID otherJob = insertJob(other);

		assertRejected(FOREIGN_KEY, () -> insertShift(user, otherJob, "2026-11-02T10:00", "2026-11-02T12:00"));
		UUID shift = insertShift(user, job, "2026-11-02T10:00", "2026-11-02T12:00");
		assertRejected(FOREIGN_KEY,
				() -> jdbc.sql("UPDATE shifts SET job_id = ? WHERE id = ?").params(otherJob, shift).update());
	}

	@Test
	void updatedAtChangesOnEveryUpdate() {
		UUID shift = insertShift(user, job, "2026-11-02T10:00", "2026-11-02T12:00");
		Map<String, Object> inserted = timestamps(shift);

		jdbc.sql("UPDATE shifts SET note = 'first' WHERE id = ?").param(shift).update();
		Map<String, Object> first = timestamps(shift);
		jdbc.sql("UPDATE shifts SET note = 'first' WHERE id = ?").param(shift).update();
		Map<String, Object> second = timestamps(shift);

		assertThat(first.get("created_at")).isEqualTo(inserted.get("created_at"));
		assertThat(second.get("created_at")).isEqualTo(inserted.get("created_at"));
		assertThat((OffsetDateTime) first.get("updated_at")).isAfter((OffsetDateTime) inserted.get("updated_at"));
		assertThat((OffsetDateTime) second.get("updated_at")).isAfter((OffsetDateTime) first.get("updated_at"));
	}

	@Test
	void jobWithShiftsCannotBeDeleted() {
		UUID unused = insertJob(user);
		insertShift(user, job, "2026-11-02T10:00", "2026-11-02T12:00");

		assertRejected(FOREIGN_KEY, () -> jdbc.sql("DELETE FROM jobs WHERE id = ?").param(job).update());
		assertThat(jdbc.sql("DELETE FROM jobs WHERE id = ?").param(unused).update()).isEqualTo(1);
	}

	@Test
	void deletingAUserRemovesAllTheirRowsAndNothingElse() {
		UUID other = insertUser("other@example.com");
		UUID otherJob = insertJob(other);
		insertShift(user, job, "2026-11-02T10:00", "2026-11-02T12:00");
		insertShift(other, otherJob, "2026-11-02T10:00", "2026-11-02T12:00");
		insertVacation(user, "2026-12-21", "2027-01-06");
		insertVacation(other, "2026-12-21", "2027-01-06");

		assertThat(jdbc.sql("DELETE FROM users WHERE id = ?").param(user).update()).isEqualTo(1);

		for (String table : new String[] { "jobs", "shifts", "vacation_periods" }) {
			assertThat(jdbc.sql("SELECT user_id FROM " + table).query(UUID.class).list()).as(table)
				.containsOnly(other)
				.isNotEmpty();
		}
		assertThat(jdbc.sql("SELECT id FROM users").query(UUID.class).list()).containsExactly(other);
	}

	private Map<String, Object> validRow(String table) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.randomUUID());
		switch (table) {
			case "users" -> {
				row.put("email", UUID.randomUUID() + "@example.com");
				row.put("password_hash", "hash");
			}
			case "jobs" -> {
				row.put("user_id", user);
				row.put("name", "Cafe");
				row.put("hourly_wage_yen", 1200);
			}
			case "shifts" -> {
				LocalDate day = LocalDate.of(2027, 1, 1).plusDays(shiftDay++);
				row.put("user_id", user);
				row.put("job_id", job);
				row.put("start_at", day.atTime(10, 0).atOffset(JST));
				row.put("end_at", day.atTime(12, 0).atOffset(JST));
			}
			case "vacation_periods" -> {
				row.put("user_id", user);
				row.put("start_date", LocalDate.of(2026, 12, 21));
				row.put("end_date", LocalDate.of(2027, 1, 6));
			}
			default -> throw new IllegalArgumentException(table);
		}
		return row;
	}

	private void insert(String table, Map<String, Object> row) {
		String columns = String.join(", ", row.keySet());
		String values = row.keySet().stream().map(column -> ":" + column).collect(Collectors.joining(", "));
		jdbc.sql("INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ")").params(row).update();
	}

	private UUID insertUser(String email) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO users (id, email, password_hash) VALUES (?, ?, 'hash')").params(id, email).update();
		return id;
	}

	private UUID insertJob(UUID userId) {
		return insertJob(userId, 1200, 0);
	}

	private UUID insertJob(UUID userId, int hourlyWageYen, int transportYen) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO jobs (id, user_id, name, hourly_wage_yen, transport_yen) VALUES (?, ?, 'Cafe', ?, ?)")
			.params(id, userId, hourlyWageYen, transportYen)
			.update();
		return id;
	}

	private UUID insertShift(UUID userId, UUID jobId, String start, String end) {
		return insertShift(userId, jobId, start, end, null, null);
	}

	private UUID insertShift(UUID userId, UUID jobId, String start, String end, String breakStart, String breakEnd) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO shifts (id, user_id, job_id, start_at, end_at, break_start, break_end)
				VALUES (:id, :userId, :jobId, :start, :end, :breakStart, :breakEnd)""")
			.param("id", id)
			.param("userId", userId)
			.param("jobId", jobId)
			.param("start", jst(start))
			.param("end", jst(end))
			.param("breakStart", breakStart == null ? null : jst(breakStart))
			.param("breakEnd", breakEnd == null ? null : jst(breakEnd))
			.update();
		return id;
	}

	private void insertVacation(UUID userId, String start, String end) {
		jdbc.sql("INSERT INTO vacation_periods (id, user_id, start_date, end_date) VALUES (?, ?, ?, ?)")
			.params(UUID.randomUUID(), userId, LocalDate.parse(start), LocalDate.parse(end))
			.update();
	}

	private Map<String, Object> timestamps(UUID shift) {
		return jdbc.sql("SELECT created_at, updated_at FROM shifts WHERE id = ?")
			.param(shift)
			.query((rs, row) -> Map.<String, Object>of("created_at", rs.getObject("created_at", OffsetDateTime.class),
					"updated_at", rs.getObject("updated_at", OffsetDateTime.class)))
			.single();
	}

	private static OffsetDateTime jst(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atOffset(JST);
	}

}
