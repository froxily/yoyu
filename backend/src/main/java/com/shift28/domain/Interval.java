package com.shift28.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record Interval(Instant start, Instant end) {

	public Interval {
		if (start == null || end == null || !end.isAfter(start)) {
			throw new IllegalArgumentException("an interval must end after it starts");
		}
	}

	public int minutes() {
		return Math.toIntExact(Duration.between(start, end).toMinutes());
	}

	public LocalDate startDate() {
		return LocalDate.ofInstant(start, Rules.ZONE);
	}

	public LocalTime startTime() {
		return LocalTime.ofInstant(start, Rules.ZONE);
	}

	public List<Interval> splitAt(LocalTime... times) {
		List<LocalTime> cutTimes = Stream.of(times).sorted().toList();
		LocalDate lastDate = LocalDate.ofInstant(end, Rules.ZONE);
		List<Interval> parts = new ArrayList<>();
		Instant cursor = start;
		for (LocalDate date = startDate(); !date.isAfter(lastDate); date = date.plusDays(1)) {
			for (LocalTime time : cutTimes) {
				Instant cut = date.atTime(time).atZone(Rules.ZONE).toInstant();
				if (cut.isAfter(cursor) && cut.isBefore(end)) {
					parts.add(new Interval(cursor, cut));
					cursor = cut;
				}
			}
		}
		parts.add(new Interval(cursor, end));
		return List.copyOf(parts);
	}

}
