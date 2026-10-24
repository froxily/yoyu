package com.shift28.app;

import java.time.Instant;
import java.util.UUID;

import com.shift28.domain.Shift;

public record ShiftInput(UUID jobId, Instant startAt, Instant endAt, Instant breakStart, Instant breakEnd, String note) {

	public Shift toShift(UUID id) {
		return new Shift(id, jobId, startAt, endAt, breakStart, breakEnd);
	}

}
