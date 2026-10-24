package com.shift28.domain;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.ThrowableTypeAssert;

final class Fixtures {

	static final UUID JOB_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	static final UUID JOB_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	static final VacationPeriod WINTER = new VacationPeriod(date("2026-12-21"), date("2027-01-06"));

	private Fixtures() {
	}

	static LocalDate date(String isoDate) {
		return LocalDate.parse(isoDate);
	}

	static Instant jst(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(Rules.ZONE).toInstant();
	}

	static Shift shift(String start, String end) {
		return shift(JOB_A, start, end);
	}

	static Shift shift(UUID jobId, String start, String end) {
		return new Shift(UUID.randomUUID(), jobId, jst(start), jst(end));
	}

	static Shift shift(String start, String end, String breakStart, String breakEnd) {
		return new Shift(UUID.randomUUID(), JOB_A, jst(start), jst(end), jst(breakStart), jst(breakEnd));
	}

	static List<Shift> daily(String firstDate, String lastDate, String startTime, String endTime) {
		return daily(JOB_A, firstDate, lastDate, startTime, endTime);
	}

	static List<Shift> daily(UUID jobId, String firstDate, String lastDate, String startTime, String endTime) {
		return date(firstDate).datesUntil(date(lastDate).plusDays(1))
			.map(day -> shift(jobId, day + "T" + startTime, day + "T" + endTime))
			.toList();
	}

	@SafeVarargs
	static List<Shift> all(List<Shift>... groups) {
		return Arrays.stream(groups).flatMap(List::stream).toList();
	}

	static ThrowableTypeAssert<DomainValidationException> assertInvalid() {
		return assertThatExceptionOfType(DomainValidationException.class);
	}

}
