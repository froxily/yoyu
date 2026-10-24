package com.shift28;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class MutableClock extends Clock {

	private volatile Instant instant;

	public MutableClock(Instant instant) {
		this.instant = instant;
	}

	public void set(Instant instant) {
		this.instant = instant;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(instant, zone);
	}

	@Override
	public Instant instant() {
		return instant;
	}

}
