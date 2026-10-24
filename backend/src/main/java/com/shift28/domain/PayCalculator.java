package com.shift28.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PayCalculator {

	private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

	private PayCalculator() {
	}

	public static PayEstimate estimate(Collection<Shift> shifts, Map<UUID, JobRate> rates, LocalDate from,
			LocalDate to) {
		List<Shift> selected = shifts.stream()
			.filter(shift -> !shift.startDate().isBefore(from) && !shift.startDate().isAfter(to))
			.sorted(Comparator.comparing(Shift::startAt))
			.toList();
		Map<WorkDay, Integer> workedSoFar = new HashMap<>();
		List<ShiftPay> shiftPays = new ArrayList<>();
		for (Shift shift : selected) {
			JobRate rate = rates.get(shift.jobId());
			if (rate == null) {
				throw new IllegalArgumentException("no rate for job " + shift.jobId());
			}
			WorkDay workDay = new WorkDay(shift.jobId(), shift.startDate());
			PayMinutes minutes = categorize(shift, workedSoFar.getOrDefault(workDay, 0));
			workedSoFar.merge(workDay, shift.workedMinutes(), Integer::sum);
			shiftPays.add(new ShiftPay(shift.id(), shift.jobId(), minutes, workPay(minutes, rate), rate.transportYen()));
		}
		return new PayEstimate(shiftPays, byJob(shiftPays));
	}

	private static PayMinutes categorize(Shift shift, int workedBefore) {
		PayMinutes minutes = PayMinutes.ZERO;
		int worked = workedBefore;
		for (Interval interval : shift.workedIntervals()) {
			for (Interval part : interval.splitAt(Rules.LATE_NIGHT_END, Rules.LATE_NIGHT_START)) {
				boolean lateNight = isLateNight(part.startTime());
				int regular = Math.clamp(Rules.OVERTIME_THRESHOLD - worked, 0, part.minutes());
				minutes = minutes.plus(PayCategory.of(false, lateNight), regular)
					.plus(PayCategory.of(true, lateNight), part.minutes() - regular);
				worked += part.minutes();
			}
		}
		return minutes;
	}

	private static boolean isLateNight(LocalTime time) {
		return !time.isBefore(Rules.LATE_NIGHT_START) || time.isBefore(Rules.LATE_NIGHT_END);
	}

	private static long workPay(PayMinutes minutes, JobRate rate) {
		BigDecimal weightedMinutes = Arrays.stream(PayCategory.values())
			.map(category -> BigDecimal.valueOf(minutes.of(category)).multiply(category.multiplier()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return weightedMinutes.multiply(BigDecimal.valueOf(rate.hourlyWageYen()))
			.divide(MINUTES_PER_HOUR, 0, RoundingMode.HALF_UP)
			.longValueExact();
	}

	private static List<JobPay> byJob(List<ShiftPay> shiftPays) {
		Map<UUID, JobPay> jobs = new LinkedHashMap<>();
		for (ShiftPay pay : shiftPays) {
			jobs.merge(pay.jobId(), JobPay.of(pay), JobPay::plus);
		}
		return List.copyOf(jobs.values());
	}

	private record WorkDay(UUID jobId, LocalDate date) {
	}

}
