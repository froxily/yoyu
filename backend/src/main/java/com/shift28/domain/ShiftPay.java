package com.shift28.domain;

import java.util.UUID;

public record ShiftPay(UUID shiftId, UUID jobId, PayMinutes minutes, long workPayYen, long transportYen) {

	public long totalYen() {
		return workPayYen + transportYen;
	}

}
