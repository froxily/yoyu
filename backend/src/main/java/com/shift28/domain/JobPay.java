package com.shift28.domain;

import java.util.UUID;

public record JobPay(UUID jobId, PayMinutes minutes, long workPayYen, long transportYen) {

	static JobPay of(ShiftPay shift) {
		return new JobPay(shift.jobId(), shift.minutes(), shift.workPayYen(), shift.transportYen());
	}

	JobPay plus(JobPay other) {
		return new JobPay(jobId, minutes.plus(other.minutes), workPayYen + other.workPayYen,
				transportYen + other.transportYen);
	}

	public long totalYen() {
		return workPayYen + transportYen;
	}

}
