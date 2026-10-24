package com.shift28.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public enum Attribution {

	CALENDAR_SPLIT {
		@Override
		public Map<LocalDate, Integer> minutesByDate(Shift shift) {
			Map<LocalDate, Integer> minutes = new TreeMap<>();
			shift.workedIntervals()
				.stream()
				.flatMap(worked -> worked.splitAt(LocalTime.MIDNIGHT).stream())
				.forEach(part -> minutes.merge(part.startDate(), part.minutes(), Integer::sum));
			return Collections.unmodifiableMap(minutes);
		}
	},

	START_DAY {
		@Override
		public Map<LocalDate, Integer> minutesByDate(Shift shift) {
			return Map.of(shift.startDate(), shift.workedMinutes());
		}
	};

	public abstract Map<LocalDate, Integer> minutesByDate(Shift shift);

}
