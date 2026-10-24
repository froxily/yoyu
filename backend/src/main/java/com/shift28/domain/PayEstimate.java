package com.shift28.domain;

import java.util.List;

public record PayEstimate(List<ShiftPay> shifts, List<JobPay> jobs) {

	public PayEstimate {
		shifts = List.copyOf(shifts);
		jobs = List.copyOf(jobs);
	}

	public long totalYen() {
		return jobs.stream().mapToLong(JobPay::totalYen).sum();
	}

}
