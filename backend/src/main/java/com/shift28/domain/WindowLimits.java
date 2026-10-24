package com.shift28.domain;

import java.util.List;

record WindowLimits(int regular, int vacation) {

	static WindowLimits strictestOf(List<RuleSet> versions) {
		return new WindowLimits(versions.stream().mapToInt(RuleSet::regularWindowLimit).min().orElseThrow(),
				versions.stream().mapToInt(RuleSet::vacationWindowLimit).min().orElseThrow());
	}

}
