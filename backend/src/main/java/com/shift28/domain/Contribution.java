package com.shift28.domain;

import java.time.Instant;
import java.util.UUID;

public record Contribution(UUID shiftId, UUID jobId, Instant startAt, int minutes, boolean draft) {
}
