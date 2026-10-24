package com.shift28.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

	private static final Duration WINDOW = Duration.ofMinutes(15);

	private final RateLimiter limiter = new RateLimiter(
			Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"), ZoneOffset.UTC));

	@Test
	void accountBucketsSurviveChurnOfAttackerChosenKeys() {
		String account = RateLimiter.accountKey(UUID.randomUUID(), "login");
		for (int failure = 0; failure < 20; failure++) {
			limiter.record(account);
		}

		for (int key = 0; key <= RateLimiter.MAX_KEYS; key++) {
			limiter.record("identifier:junk_" + key + ":login");
		}

		assertThatThrownBy(() -> limiter.check(account, 20, WINDOW)).isInstanceOf(TooManyRequestsException.class);
		assertThat(limiter.size()).isEqualTo(RateLimiter.MAX_KEYS + 1);
	}

	@Test
	void globalBucketsSurviveChurnOfAttackerChosenKeys() {
		String global = RateLimiter.globalKey("oauth-start");
		for (int start = 0; start < 300; start++) {
			limiter.record(global);
		}

		for (int key = 0; key <= RateLimiter.MAX_KEYS; key++) {
			limiter.record("oauth-start:ip:junk_" + key);
		}

		assertThatThrownBy(() -> limiter.check(global, 300, WINDOW)).isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void releasingRefundsOneReservationAndKeepsTheOthers() {
		for (int attempt = 0; attempt < 5; attempt++) {
			limiter.acquire(new RateLimiter.Limit("client:203.0.113.7:account", 5, WINDOW));
		}

		limiter.release("client:203.0.113.7:account");

		limiter.acquire("client:203.0.113.7:account", 5, WINDOW);
		assertThatThrownBy(() -> limiter.check("client:203.0.113.7:account", 5, WINDOW))
			.isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void aRejectedReservationRecordsNothing() {
		limiter.acquire("login:ip:203.0.113.7", 1, WINDOW);

		assertThatThrownBy(() -> limiter.acquire(new RateLimiter.Limit("client:203.0.113.7:account", 5, WINDOW),
				new RateLimiter.Limit("login:ip:203.0.113.7", 1, WINDOW)))
			.isInstanceOf(TooManyRequestsException.class);
		limiter.check("client:203.0.113.7:account", 1, WINDOW);
	}

	@Test
	void otherKeysAreStillEvictedOldestFirst() {
		for (int key = 0; key <= RateLimiter.MAX_KEYS; key++) {
			limiter.record("login:ip:" + key);
		}

		limiter.check("login:ip:0", 1, WINDOW);
		assertThatThrownBy(() -> limiter.check("login:ip:1", 1, WINDOW)).isInstanceOf(TooManyRequestsException.class);
		assertThat(limiter.size()).isEqualTo(RateLimiter.MAX_KEYS);
	}

}
