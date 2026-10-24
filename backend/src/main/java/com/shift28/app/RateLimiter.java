package com.shift28.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

	static final int MAX_KEYS = 10_000;

	private static final String ACCOUNT_PREFIX = "account:";

	private static final String GLOBAL_PREFIX = "global:";

	private static final Duration LONGEST_WINDOW = Duration.ofHours(1);

	private final Clock clock;

	private final Map<String, Deque<Instant>> serverKeys = new HashMap<>();

	private final Map<String, Deque<Instant>> others = new LinkedHashMap<>(256, 0.75f, true) {

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
			return size() > MAX_KEYS;
		}

	};

	RateLimiter(Clock clock) {
		this.clock = clock;
	}

	public static String accountKey(UUID userId, String scope) {
		return ACCOUNT_PREFIX + userId + ":" + scope;
	}

	public static String globalKey(String scope) {
		return GLOBAL_PREFIX + scope;
	}

	public static String clientKey(String clientIp, String key) {
		return "client:" + clientIp + ":" + key;
	}

	public synchronized void check(String key, int limit, Duration window) {
		Deque<Instant> recent = recent(key, window);
		if (recent != null && recent.size() >= limit) {
			throw new TooManyRequestsException(retryAfter(recent.peekFirst(), window));
		}
	}

	public synchronized void record(String key) {
		buckets(key).computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(clock.instant());
	}

	public synchronized void acquire(String key, int limit, Duration window) {
		check(key, limit, window);
		record(key);
	}

	public synchronized void acquire(Limit... limits) {
		for (Limit limit : limits) {
			check(limit.key(), limit.max(), limit.window());
		}
		for (Limit limit : limits) {
			record(limit.key());
		}
	}

	public synchronized void release(String key) {
		Deque<Instant> times = buckets(key).get(key);
		if (times != null) {
			times.pollLast();
			if (times.isEmpty()) {
				buckets(key).remove(key);
			}
		}
	}

	public synchronized void reset(String key) {
		buckets(key).remove(key);
	}

	public synchronized void clear() {
		serverKeys.clear();
		others.clear();
	}

	synchronized int size() {
		return serverKeys.size() + others.size();
	}

	@Scheduled(fixedDelay = 600_000)
	public synchronized void sweep() {
		Instant cutoff = clock.instant().minus(LONGEST_WINDOW);
		sweep(serverKeys, cutoff);
		sweep(others, cutoff);
	}

	private static void sweep(Map<String, Deque<Instant>> buckets, Instant cutoff) {
		for (Iterator<Deque<Instant>> it = buckets.values().iterator(); it.hasNext();) {
			Deque<Instant> times = it.next();
			while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
				times.pollFirst();
			}
			if (times.isEmpty()) {
				it.remove();
			}
		}
	}

	private Map<String, Deque<Instant>> buckets(String key) {
		return key.startsWith(ACCOUNT_PREFIX) || key.startsWith(GLOBAL_PREFIX) ? serverKeys : others;
	}

	private Deque<Instant> recent(String key, Duration window) {
		Deque<Instant> times = buckets(key).get(key);
		if (times == null) {
			return null;
		}
		Instant cutoff = clock.instant().minus(window);
		while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
			times.pollFirst();
		}
		return times;
	}

	private long retryAfter(Instant oldest, Duration window) {
		long seconds = Duration.between(clock.instant(), oldest.plus(window)).toSeconds();
		return Math.max(1, seconds + 1);
	}

	public record Limit(String key, int max, Duration window) {
	}

}
