package com.shift28.persistence;

import static com.shift28.persistence.SqlStates.EXCLUSION;
import static com.shift28.persistence.SqlStates.UNIQUE;
import static com.shift28.persistence.SqlStates.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shift28.IntegrationTest;

@IntegrationTest
class RepositoriesTest {

	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

	@Autowired
	UserRepository users;

	@Autowired
	JobRepository jobs;

	@Autowired
	ShiftRepository shifts;

	@Autowired
	VacationPeriodRepository vacations;

	@Autowired
	JdbcClient jdbc;

	private UUID alice;

	private UUID bob;

	private UUID aliceJob;

	private UUID bobJob;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE users CASCADE").update();
		alice = users.save(new UserEntity("alice@example.com", "hash")).getId();
		bob = users.save(new UserEntity("bob@example.com", "hash")).getId();
		aliceJob = jobs.save(new JobEntity(alice, "Cafe", 1200, 300, "#3b82f6")).getId();
		bobJob = jobs.save(new JobEntity(bob, "Bar", 1300, 0, "#ef4444")).getId();
	}

	@Test
	void usersAreFoundByEmailIgnoringCase() {
		assertThat(users.findByEmail("ALICE@Example.com")).map(UserEntity::getId).contains(alice);
		assertThat(users.findByEmail("carol@example.com")).isEmpty();
	}

	@Test
	void duplicateEmailIsRejected() {
		assertRejected(UNIQUE, () -> users.saveAndFlush(new UserEntity("Alice@example.com", "hash")));
	}

	@Test
	void savedEntitiesGetGeneratedIdsAndDatabaseTimestamps() {
		JobEntity job = jobs.findById(aliceJob).orElseThrow();
		ShiftEntity shift = shifts.save(shift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T12:00"));

		assertThat(job.getCreatedAt()).isNotNull();
		assertThat(shift.getId()).isNotNull();
		assertThat(shift.getCreatedAt()).isNotNull();
		assertThat(shift.getUpdatedAt()).isNotNull();
	}

	@Test
	void jobsAreScopedToTheirUserAndArchivedOnesCanBeLeftOut() {
		JobEntity bookshop = new JobEntity(alice, "Bookshop", 1100, 0, "#22c55e");
		bookshop.archive();
		jobs.save(bookshop);

		assertThat(jobs.findByUserIdOrderByName(alice)).extracting(JobEntity::getName).containsExactly("Bookshop", "Cafe");
		assertThat(jobs.findByUserIdAndArchivedFalseOrderByName(alice)).extracting(JobEntity::getName)
			.containsExactly("Cafe");
		assertThat(jobs.findByIdAndUserId(aliceJob, alice)).isPresent();
		assertThat(jobs.findByIdAndUserId(aliceJob, bob)).isEmpty();
	}

	@Test
	void jobUpdatesArePersisted() {
		JobEntity job = jobs.findByIdAndUserId(aliceJob, alice).orElseThrow();
		job.update("Cafe Nova", 1250, 400, "#a855f7");
		jobs.save(job);

		JobEntity loaded = jobs.findById(aliceJob).orElseThrow();
		assertThat(loaded.getName()).isEqualTo("Cafe Nova");
		assertThat(loaded.getHourlyWageYen()).isEqualTo(1250);
		assertThat(loaded.getTransportYen()).isEqualTo(400);
		assertThat(loaded.getColor()).isEqualTo("#a855f7");
		assertThat(loaded.isArchived()).isFalse();
	}

	@Test
	void shiftsRoundTripTheirInstantsBreakAndNote() {
		UUID id = shifts.save(new ShiftEntity(alice, aliceJob, jst("2026-11-07T20:00"), jst("2026-11-08T04:00"),
				jst("2026-11-07T23:30"), jst("2026-11-08T00:30"), "closing")).getId();

		ShiftEntity loaded = shifts.findByIdAndUserId(id, alice).orElseThrow();
		assertThat(loaded.getUserId()).isEqualTo(alice);
		assertThat(loaded.getJobId()).isEqualTo(aliceJob);
		assertThat(loaded.getStartAt()).isEqualTo(jst("2026-11-07T20:00"));
		assertThat(loaded.getEndAt()).isEqualTo(jst("2026-11-08T04:00"));
		assertThat(loaded.getBreakStart()).isEqualTo(jst("2026-11-07T23:30"));
		assertThat(loaded.getBreakEnd()).isEqualTo(jst("2026-11-08T00:30"));
		assertThat(loaded.getNote()).isEqualTo("closing");
		assertThat(shifts.findByIdAndUserId(id, bob)).isEmpty();
	}

	@Test
	void shiftUpdatesArePersistedAndRefreshUpdatedAt() {
		ShiftEntity saved = shifts.save(shift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T12:00"));

		ShiftEntity shift = shifts.findByIdAndUserId(saved.getId(), alice).orElseThrow();
		shift.update(aliceJob, jst("2026-11-02T11:00"), jst("2026-11-02T15:00"), jst("2026-11-02T13:00"),
				jst("2026-11-02T13:30"), "moved");
		ShiftEntity updated = shifts.save(shift);

		ShiftEntity loaded = shifts.findById(saved.getId()).orElseThrow();
		assertThat(updated.getUpdatedAt()).isEqualTo(loaded.getUpdatedAt());
		assertThat(loaded.getStartAt()).isEqualTo(jst("2026-11-02T11:00"));
		assertThat(loaded.getEndAt()).isEqualTo(jst("2026-11-02T15:00"));
		assertThat(loaded.getBreakStart()).isEqualTo(jst("2026-11-02T13:00"));
		assertThat(loaded.getBreakEnd()).isEqualTo(jst("2026-11-02T13:30"));
		assertThat(loaded.getNote()).isEqualTo("moved");
		assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(loaded.getUpdatedAt()).isAfter(saved.getUpdatedAt());
	}

	@Test
	void shiftsOverlappingARangeAreScopedToTheUserAndOrderedByStart() {
		ShiftEntity acrossStart = shifts.save(shift(alice, aliceJob, "2026-11-01T22:00", "2026-11-02T01:00"));
		ShiftEntity acrossEnd = shifts.save(shift(alice, aliceJob, "2026-11-02T23:00", "2026-11-03T01:00"));
		ShiftEntity inside = shifts.save(shift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T14:00"));
		shifts.save(shift(alice, aliceJob, "2026-11-01T18:00", "2026-11-01T22:00"));
		shifts.save(shift(alice, aliceJob, "2026-11-03T01:00", "2026-11-03T03:00"));
		shifts.save(shift(bob, bobJob, "2026-11-02T10:00", "2026-11-02T14:00"));

		assertThat(shifts.findOverlapping(alice, jst("2026-11-02T00:00"), jst("2026-11-03T00:00")))
			.extracting(ShiftEntity::getId)
			.containsExactly(acrossStart.getId(), inside.getId(), acrossEnd.getId());
		assertThat(shifts.findOverlapping(alice, jst("2026-11-02T01:00"), jst("2026-11-02T10:00"))).isEmpty();
	}

	@Test
	@DisplayName("T16 through JPA: saving an overlapping shift fails with the exclusion constraint")
	void overlappingShiftIsRejectedThroughJpa() {
		shifts.save(shift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T14:00"));

		assertRejected(EXCLUSION,
				() -> shifts.saveAndFlush(shift(alice, aliceJob, "2026-11-02T13:00", "2026-11-02T15:00")));
		assertThat(shifts.saveAndFlush(shift(alice, aliceJob, "2026-11-02T14:00", "2026-11-02T16:00")).getId())
			.isNotNull();
	}

	@Test
	void jobsWithShiftsAreDetectedPerUser() {
		UUID unused = jobs.save(new JobEntity(alice, "Bookshop", 1100, 0, "#22c55e")).getId();
		shifts.save(shift(alice, aliceJob, "2026-11-02T10:00", "2026-11-02T12:00"));

		assertThat(shifts.existsByUserIdAndJobId(alice, aliceJob)).isTrue();
		assertThat(shifts.existsByUserIdAndJobId(alice, unused)).isFalse();
		assertThat(shifts.existsByUserIdAndJobId(bob, aliceJob)).isFalse();
	}

	@Test
	void vacationsAreScopedToTheirUserAndOrderedByStart() {
		UUID winter = vacations.save(new VacationPeriodEntity(alice, date("2026-12-21"), date("2027-01-06"), "Winter"))
			.getId();
		UUID summer = vacations.save(new VacationPeriodEntity(alice, date("2026-08-01"), date("2026-09-20"), null))
			.getId();
		vacations.save(new VacationPeriodEntity(bob, date("2026-08-01"), date("2026-09-20"), "Bob"));

		assertThat(vacations.findByUserIdOrderByStartDate(alice)).extracting(VacationPeriodEntity::getId)
			.containsExactly(summer, winter);
		assertThat(vacations.findByIdAndUserId(winter, alice)).map(VacationPeriodEntity::getLabel).contains("Winter");
		assertThat(vacations.findByIdAndUserId(winter, bob)).isEmpty();
	}

	@Test
	void vacationUpdatesArePersisted() {
		VacationPeriodEntity vacation = vacations
			.save(new VacationPeriodEntity(alice, date("2026-12-21"), date("2027-01-06"), "Winter"));
		vacation.update(date("2026-12-23"), date("2027-01-07"), "Winter break");
		vacations.save(vacation);

		VacationPeriodEntity loaded = vacations.findById(vacation.getId()).orElseThrow();
		assertThat(loaded.getStartDate()).isEqualTo(date("2026-12-23"));
		assertThat(loaded.getEndDate()).isEqualTo(date("2027-01-07"));
		assertThat(loaded.getLabel()).isEqualTo("Winter break");
	}

	private static ShiftEntity shift(UUID userId, UUID jobId, String start, String end) {
		return new ShiftEntity(userId, jobId, jst(start), jst(end), null, null, null);
	}

	private static Instant jst(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(TOKYO).toInstant();
	}

	private static LocalDate date(String isoDate) {
		return LocalDate.parse(isoDate);
	}

}
