package com.shift28.domain;

public record PayMinutes(long normal, long lateNight, long overtime, long overtimeLateNight) {

	public static final PayMinutes ZERO = new PayMinutes(0, 0, 0, 0);

	public long of(PayCategory category) {
		return switch (category) {
			case NORMAL -> normal;
			case LATE_NIGHT -> lateNight;
			case OVERTIME -> overtime;
			case OVERTIME_LATE_NIGHT -> overtimeLateNight;
		};
	}

	public PayMinutes plus(PayCategory category, long minutes) {
		return switch (category) {
			case NORMAL -> new PayMinutes(normal + minutes, lateNight, overtime, overtimeLateNight);
			case LATE_NIGHT -> new PayMinutes(normal, lateNight + minutes, overtime, overtimeLateNight);
			case OVERTIME -> new PayMinutes(normal, lateNight, overtime + minutes, overtimeLateNight);
			case OVERTIME_LATE_NIGHT -> new PayMinutes(normal, lateNight, overtime, overtimeLateNight + minutes);
		};
	}

	public PayMinutes plus(PayMinutes other) {
		return new PayMinutes(normal + other.normal, lateNight + other.lateNight, overtime + other.overtime,
				overtimeLateNight + other.overtimeLateNight);
	}

}
