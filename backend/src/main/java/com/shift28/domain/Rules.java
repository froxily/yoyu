package com.shift28.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

public final class Rules {

	public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
	public static final int REGULAR_WINDOW_LIMIT = 1680;
	public static final int VACATION_WINDOW_LIMIT = 2400;
	public static final int VACATION_DAY_LIMIT = 480;
	public static final int WINDOW_DAYS = 7;
	public static final Duration MAX_SHIFT_LENGTH = Duration.ofHours(24);
	public static final LocalTime LATE_NIGHT_START = LocalTime.of(22, 0);
	public static final LocalTime LATE_NIGHT_END = LocalTime.of(5, 0);
	public static final int OVERTIME_THRESHOLD = 480;

	private Rules() {
	}

}
