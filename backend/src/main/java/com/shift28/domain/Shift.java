package com.shift28.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public record Shift(UUID id, UUID jobId, Instant startAt, Instant endAt, Instant breakStart, Instant breakEnd) {

	public Shift {
		if (jobId == null || startAt == null || endAt == null) {
			throw new DomainValidationException("a shift needs a job, a start and an end");
		}
		if (!endAt.isAfter(startAt)) {
			throw new DomainValidationException("a shift must end after it starts");
		}
		if (Duration.between(startAt, endAt).compareTo(Rules.MAX_SHIFT_LENGTH) > 0) {
			throw new DomainValidationException("a shift lasts at most 24 hours");
		}
		if ((breakStart == null) != (breakEnd == null)) {
			throw new DomainValidationException("a break needs both a start and an end");
		}
		if (breakStart != null
				&& (breakStart.isBefore(startAt) || !breakEnd.isAfter(breakStart) || breakEnd.isAfter(endAt))) {
			throw new DomainValidationException("a break must lie inside its shift");
		}
		if (Stream.of(startAt, endAt, breakStart, breakEnd).filter(Objects::nonNull).anyMatch(Shift::hasSeconds)) {
			throw new DomainValidationException("shift times must be whole minutes");
		}
	}

	public Shift(UUID id, UUID jobId, Instant startAt, Instant endAt) {
		this(id, jobId, startAt, endAt, null, null);
	}

	public List<Interval> workedIntervals() {
		if (breakStart == null) {
			return List.of(new Interval(startAt, endAt));
		}
		List<Interval> worked = new ArrayList<>(2);
		if (breakStart.isAfter(startAt)) {
			worked.add(new Interval(startAt, breakStart));
		}
		if (endAt.isAfter(breakEnd)) {
			worked.add(new Interval(breakEnd, endAt));
		}
		return List.copyOf(worked);
	}

	public int workedMinutes() {
		return workedIntervals().stream().mapToInt(Interval::minutes).sum();
	}

	public LocalDate startDate() {
		return LocalDate.ofInstant(startAt, Rules.ZONE);
	}

	public LocalDate lastDate() {
		return LocalDate.ofInstant(endAt.minusNanos(1), Rules.ZONE);
	}

	public boolean overlaps(Shift other) {
		return startAt.isBefore(other.endAt) && other.startAt.isBefore(endAt);
	}

	private static boolean hasSeconds(Instant instant) {
		return instant.getEpochSecond() % 60 != 0 || instant.getNano() != 0;
	}

}
