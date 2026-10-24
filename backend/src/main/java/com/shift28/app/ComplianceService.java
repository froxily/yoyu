package com.shift28.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shift28.domain.Attribution;
import com.shift28.domain.Check;
import com.shift28.domain.ComplianceEngine;
import com.shift28.domain.ComplianceReport;
import com.shift28.domain.RuleBook;
import com.shift28.domain.Rules;
import com.shift28.domain.Shift;
import com.shift28.domain.Status;
import com.shift28.persistence.JobEntity;
import com.shift28.persistence.JobRepository;
import com.shift28.persistence.RuleVersionEntity;
import com.shift28.persistence.RuleVersionRepository;
import com.shift28.persistence.ShiftEntity;
import com.shift28.persistence.ShiftRepository;
import com.shift28.persistence.VacationPeriodEntity;
import com.shift28.persistence.VacationPeriodRepository;

@Service
@Transactional(readOnly = true)
public class ComplianceService {

	private static final int CAPACITY_DAYS_AFTER_TODAY = 7;

	private final ShiftRepository shifts;

	private final JobRepository jobs;

	private final VacationPeriodRepository vacations;

	private final RuleVersionRepository ruleVersions;

	private final Clock clock;

	ComplianceService(ShiftRepository shifts, JobRepository jobs, VacationPeriodRepository vacations,
			RuleVersionRepository ruleVersions, Clock clock) {
		this.shifts = shifts;
		this.jobs = jobs;
		this.vacations = vacations;
		this.ruleVersions = ruleVersions;
		this.clock = clock;
	}

	public Overview overview(UUID userId, LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.ofInstant(clock.instant(), Rules.ZONE);
		LocalDate lastCapacityDate = today.plusDays(CAPACITY_DAYS_AFTER_TODAY);
		ComplianceEngine engine = engine(userId, earlier(from, today), later(to, lastCapacityDate));
		ComplianceReport report = engine.evaluate(from, to);
		List<Day> days = dates(from, to)
			.map(date -> new Day(date, engine.minutesOn(date, Attribution.CALENDAR_SPLIT), engine.isVacation(date),
					engine.statusOn(date)))
			.toList();
		List<Capacity> capacity = dates(today, lastCapacityDate)
			.map(date -> new Capacity(date, engine.remainingMinutes(date)))
			.toList();
		List<Check> issues = report.issues();
		return new Overview(days, issues, capacity, jobNames(userId, issues));
	}

	public DraftCheck check(UUID userId, Shift draft, UUID excludeShiftId) {
		jobs.findByIdAndUserId(draft.jobId(), userId).orElseThrow(() -> new NotFoundException("Job not found."));
		ComplianceEngine engine = engine(userId, draft.startDate(), draft.lastDate());
		if (excludeShiftId == null) {
			ComplianceReport report = engine.check(draft);
			return new DraftCheck(report, engine.overlapping(draft), jobNames(userId, report.issues()));
		}
		shifts.findByIdAndUserId(excludeShiftId, userId).orElseThrow(() -> new NotFoundException("Shift not found."));
		ComplianceReport report = engine.check(draft, excludeShiftId);
		return new DraftCheck(report, engine.overlapping(draft, excludeShiftId), jobNames(userId, report.issues()));
	}

	private Map<UUID, String> jobNames(UUID userId, List<Check> issues) {
		if (issues.isEmpty()) {
			return Map.of();
		}
		return jobs.findByUserIdOrderByName(userId)
			.stream()
			.collect(Collectors.toMap(JobEntity::getId, JobEntity::getName));
	}

	static Instant startOf(LocalDate date) {
		return date.atStartOfDay(Rules.ZONE).toInstant();
	}

	private ComplianceEngine engine(UUID userId, LocalDate first, LocalDate last) {
		List<Shift> loaded = shifts
			.findOverlapping(userId, startOf(first.minusDays(Rules.WINDOW_DAYS)),
					startOf(last.plusDays(Rules.WINDOW_DAYS + 1)))
			.stream()
			.map(ShiftEntity::toDomain)
			.toList();
		return new ComplianceEngine(loaded,
				vacations.findByUserIdOrderByStartDate(userId).stream().map(VacationPeriodEntity::toDomain).toList(),
				new RuleBook(ruleVersions.findAll().stream().map(RuleVersionEntity::toDomain).toList()));
	}

	private static Stream<LocalDate> dates(LocalDate first, LocalDate last) {
		return first.datesUntil(last.plusDays(1));
	}

	private static LocalDate earlier(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	private static LocalDate later(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	public record Overview(List<Day> days, List<Check> issues, List<Capacity> capacity, Map<UUID, String> jobNames) {
	}

	public record Day(LocalDate date, long minutes, boolean vacation, Status status) {
	}

	public record Capacity(LocalDate date, long remainingMinutes) {
	}

}
