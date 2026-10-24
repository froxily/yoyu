package com.shift28.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class RuleBook {

	private final NavigableMap<LocalDate, RuleSet> versions = new TreeMap<>();

	public RuleBook(Collection<RuleSet> versions) {
		for (RuleSet version : versions) {
			if (this.versions.put(version.effectiveFrom(), version) != null) {
				throw new IllegalArgumentException("two rule versions take effect on " + version.effectiveFrom());
			}
		}
		if (this.versions.isEmpty()) {
			throw new IllegalArgumentException("a rule book needs at least one version");
		}
	}

	public static RuleBook baseline() {
		return new RuleBook(List.of(RuleSet.BASELINE));
	}

	public RuleSet on(LocalDate date) {
		Map.Entry<LocalDate, RuleSet> version = versions.floorEntry(date);
		return version == null ? versions.firstEntry().getValue() : version.getValue();
	}

	public List<RuleSet> versionsBetween(LocalDate first, LocalDate last) {
		return Stream.concat(Stream.of(on(first)), versions.subMap(first, false, last, true).values().stream())
			.distinct()
			.toList();
	}

}
