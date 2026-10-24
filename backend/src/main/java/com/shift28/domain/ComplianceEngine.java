package com.shift28.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class ComplianceEngine {

	private final List<Shift> shifts;

	private final List<VacationPeriod> vacations;

	private final RuleBook rules;

	private final Shift draft;

	private final Map<Attribution, DailyTotals> totals = new EnumMap<>(Attribution.class);

	public ComplianceEngine(Collection<Shift> shifts, Collection<VacationPeriod> vacations) {
		this(shifts, vacations, RuleBook.baseline());
	}

	public ComplianceEngine(Collection<Shift> shifts, Collection<VacationPeriod> vacations, RuleBook rules) {
		this(shifts, vacations, rules, null);
	}

	private ComplianceEngine(Collection<Shift> shifts, Collection<VacationPeriod> vacations, RuleBook rules,
			Shift draft) {
		this.shifts = List.copyOf(shifts);
		this.vacations = List.copyOf(vacations);
		this.rules = rules;
		this.draft = draft;
		for (Attribution attribution : Attribution.values()) {
			totals.put(attribution, DailyTotals.of(this.shifts, attribution));
		}
	}

	public ComplianceReport evaluate(LocalDate from, LocalDate to) {
		List<Check> windows = windowStarts(from, to).map(this::window).toList();
		List<Check> days = from.datesUntil(to.plusDays(1)).filter(this::isVacation).map(this::day).toList();
		return new ComplianceReport(windows, days);
	}

	public long remainingMinutes(LocalDate date) {
		long remaining = Long.MAX_VALUE;
		for (DailyTotals daily : totals.values()) {
			for (LocalDate start : windowStarts(date, date).toList()) {
				remaining = Math.min(remaining,
						kind(start).strictLimit(limits(start)) - daily.between(start, windowEnd(start)));
			}
			if (isVacation(date)) {
				remaining = Math.min(remaining, rules.on(date).vacationDayLimit() - daily.on(date));
			}
		}
		return Math.max(0, remaining);
	}

	public long minutesOn(LocalDate date, Attribution attribution) {
		return totals.get(attribution).on(date);
	}

	public Status statusOn(LocalDate date) {
		boolean worked = totals.values().stream().anyMatch(daily -> daily.on(date) > 0);
		return worked ? evaluate(date, date).worstStatus() : Status.OK;
	}

	public List<Shift> overlapping(Shift draft) {
		return overlapping(draft, shifts);
	}

	public List<Shift> overlapping(Shift draft, UUID excludeShiftId) {
		return overlapping(draft, without(excludeShiftId));
	}

	public ComplianceReport check(Shift draft) {
		return evaluateWith(draft, shifts);
	}

	public ComplianceReport check(Shift draft, UUID excludeShiftId) {
		return evaluateWith(draft, without(excludeShiftId));
	}

	private List<Shift> without(UUID excludeShiftId) {
		return shifts.stream().filter(shift -> !excludeShiftId.equals(shift.id())).toList();
	}

	private static List<Shift> overlapping(Shift draft, List<Shift> others) {
		return others.stream().filter(draft::overlaps).sorted(Comparator.comparing(Shift::startAt)).toList();
	}

	private ComplianceReport evaluateWith(Shift draft, List<Shift> others) {
		List<Shift> withDraft = Stream.concat(others.stream(), Stream.of(draft)).toList();
		return new ComplianceEngine(withDraft, vacations, rules, draft).evaluate(draft.startDate(), draft.lastDate());
	}

	private Check window(LocalDate start) {
		LocalDate end = windowEnd(start);
		WindowKind kind = kind(start);
		List<RuleSet> versions = rules.versionsBetween(start, end);
		WindowLimits limits = WindowLimits.strictestOf(versions);
		List<Assessment> assessments = totals.entrySet()
			.stream()
			.map(entry -> kind.assess(limits, entry.getKey(), entry.getValue().between(start, end)))
			.toList();
		return explained(new Check(CheckType.WINDOW, start, end, assessments, effectiveDates(versions), List.of()));
	}

	private Check day(LocalDate date) {
		RuleSet version = rules.on(date);
		int limit = version.vacationDayLimit();
		List<Assessment> assessments = totals.entrySet().stream().map(entry -> {
			long minutes = entry.getValue().on(date);
			return new Assessment(entry.getKey(), minutes, limit, minutes > limit ? Status.VIOLATION : Status.OK);
		}).toList();
		return explained(new Check(CheckType.DAY, date, date, assessments, List.of(version.effectiveFrom()), List.of()));
	}

	private Check explained(Check check) {
		if (check.status() == Status.OK) {
			return check;
		}
		Attribution attribution = check.reported().attribution();
		List<Contribution> contributions = shifts.stream()
			.map(shift -> new Contribution(shift.id(), shift.jobId(), shift.startAt(),
					minutesBetween(attribution, shift, check.start(), check.end()), shift == draft))
			.filter(contribution -> contribution.minutes() > 0)
			.sorted(Comparator.comparing(Contribution::startAt))
			.toList();
		return new Check(check.type(), check.start(), check.end(), check.assessments(), check.ruleVersions(),
				contributions);
	}

	private static int minutesBetween(Attribution attribution, Shift shift, LocalDate first, LocalDate last) {
		return attribution.minutesByDate(shift)
			.entrySet()
			.stream()
			.filter(entry -> !entry.getKey().isBefore(first) && !entry.getKey().isAfter(last))
			.mapToInt(Map.Entry::getValue)
			.sum();
	}

	private WindowLimits limits(LocalDate start) {
		return WindowLimits.strictestOf(rules.versionsBetween(start, windowEnd(start)));
	}

	private static List<LocalDate> effectiveDates(List<RuleSet> versions) {
		return versions.stream().map(RuleSet::effectiveFrom).toList();
	}

	private WindowKind kind(LocalDate start) {
		return WindowKind.of(start.datesUntil(windowEnd(start).plusDays(1)).filter(this::isVacation).count());
	}

	public boolean isVacation(LocalDate date) {
		return vacations.stream().anyMatch(vacation -> vacation.covers(date));
	}

	private static Stream<LocalDate> windowStarts(LocalDate from, LocalDate to) {
		return from.minusDays(Rules.WINDOW_DAYS - 1).datesUntil(to.plusDays(1));
	}

	private static LocalDate windowEnd(LocalDate start) {
		return start.plusDays(Rules.WINDOW_DAYS - 1);
	}

}
