package com.shift28.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class DailyTotals {

	private final Map<LocalDate, Long> minutes;

	private DailyTotals(Map<LocalDate, Long> minutes) {
		this.minutes = minutes;
	}

	static DailyTotals of(Collection<Shift> shifts, Attribution attribution) {
		Map<LocalDate, Long> minutes = new HashMap<>();
		for (Shift shift : shifts) {
			attribution.minutesByDate(shift).forEach((date, worked) -> minutes.merge(date, (long) worked, Long::sum));
		}
		return new DailyTotals(minutes);
	}

	long on(LocalDate date) {
		return minutes.getOrDefault(date, 0L);
	}

	long between(LocalDate first, LocalDate last) {
		return first.datesUntil(last.plusDays(1)).mapToLong(this::on).sum();
	}

}
