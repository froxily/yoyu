package com.shift28.domain;

enum WindowKind {

	REGULAR, VACATION, MIXED;

	static WindowKind of(long vacationDates) {
		if (vacationDates == 0) {
			return REGULAR;
		}
		return vacationDates == Rules.WINDOW_DAYS ? VACATION : MIXED;
	}

	int strictLimit(WindowLimits limits) {
		return switch (this) {
			case REGULAR -> limits.regular();
			case VACATION -> limits.vacation();
			case MIXED -> Math.min(limits.regular(), limits.vacation());
		};
	}

	private int lenientLimit(WindowLimits limits) {
		return switch (this) {
			case REGULAR -> limits.regular();
			case VACATION -> limits.vacation();
			case MIXED -> Math.max(limits.regular(), limits.vacation());
		};
	}

	Assessment assess(WindowLimits limits, Attribution attribution, long minutes) {
		int strict = strictLimit(limits);
		int lenient = lenientLimit(limits);
		if (minutes > lenient) {
			return new Assessment(attribution, minutes, lenient, Status.VIOLATION);
		}
		return new Assessment(attribution, minutes, strict, minutes > strict ? Status.AMBIGUOUS : Status.OK);
	}

}
