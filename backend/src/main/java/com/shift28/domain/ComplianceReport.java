package com.shift28.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record ComplianceReport(List<Check> windows, List<Check> days) {

	public ComplianceReport {
		windows = List.copyOf(windows);
		days = List.copyOf(days);
	}

	public Status worstStatus() {
		return checks().map(Check::status).max(Comparator.naturalOrder()).orElse(Status.OK);
	}

	public List<Check> issues() {
		return checks().filter(check -> check.status() != Status.OK).toList();
	}

	public Optional<Check> window(LocalDate start) {
		return windows.stream().filter(window -> window.start().equals(start)).findFirst();
	}

	public Optional<Check> day(LocalDate date) {
		return days.stream().filter(day -> day.start().equals(date)).findFirst();
	}

	private Stream<Check> checks() {
		return Stream.concat(windows.stream(), days.stream());
	}

}
