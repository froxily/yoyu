package com.shift28.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record Check(CheckType type, LocalDate start, LocalDate end, List<Assessment> assessments,
		List<LocalDate> ruleVersions, List<Contribution> contributions) {

	private static final Comparator<Assessment> SEVERITY = Comparator.comparing(Assessment::status)
		.thenComparingLong(Assessment::minutes)
		.thenComparing(Assessment::attribution, Comparator.reverseOrder());

	public Check {
		assessments = List.copyOf(assessments);
		ruleVersions = List.copyOf(ruleVersions);
		contributions = List.copyOf(contributions);
		if (assessments.isEmpty()) {
			throw new IllegalArgumentException("a check needs at least one assessment");
		}
		if (ruleVersions.isEmpty()) {
			throw new IllegalArgumentException("a check needs the rule versions it used");
		}
	}

	public boolean rulesChanged() {
		return ruleVersions.size() > 1;
	}

	public Assessment reported() {
		return assessments.stream().max(SEVERITY).orElseThrow();
	}

	public Status status() {
		return reported().status();
	}

	public Assessment under(Attribution attribution) {
		return assessments.stream()
			.filter(assessment -> assessment.attribution() == attribution)
			.findFirst()
			.orElseThrow();
	}

}
