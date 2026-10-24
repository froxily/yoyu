package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.shift28.app.IdempotencyService;

class IdempotencyApiTest extends ApiTestSupport {

	private static final String KEY = "4f6c2f0e-0b51-4b8e-9d0a-6f1d5c2b7a10";

	@Autowired
	IdempotencyService idempotency;

	@Test
	@DisplayName("I1: the same key and body replays the stored response without writing again")
	void theSameRequestIsReplayed() throws Exception {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		String body = shift(cafe, "2026-11-02T10:00", "2026-11-02T14:00");

		MvcTestResult first = alice.post("/api/shifts", body, KEY);
		MvcTestResult second = alice.post("/api/shifts", body, KEY);

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
		assertThat(second).hasStatus(HttpStatus.CREATED).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
		assertThat(second.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		assertThat(count("shifts")).isEqualTo(1);
	}

	@Test
	@DisplayName("I1: jobs and vacations are idempotent too")
	void jobsAndVacationsAreReplayed() {
		ApiClient alice = signedUp("alice@example.com");
		String vacation = """
				{"startDate": "2026-12-21", "endDate": "2027-01-06"}""";

		String job = createdId(alice.post("/api/jobs", job("Cafe", 1200), KEY));
		assertThat(ApiClient.<String>read(alice.post("/api/jobs", job("Cafe", 1200), KEY), "$.id")).isEqualTo(job);
		String period = createdId(alice.post("/api/vacations", vacation, "vacation-key"));
		assertThat(ApiClient.<String>read(alice.post("/api/vacations", vacation, "vacation-key"), "$.id"))
			.isEqualTo(period);

		assertThat(count("jobs")).isEqualTo(1);
		assertThat(count("vacation_periods")).isEqualTo(1);
	}

	@Test
	@DisplayName("I2: the same key with a different body is rejected and writes nothing")
	void aReusedKeyWithADifferentBodyIsRejected() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		assertThat(alice.post("/api/shifts", shift(cafe, "2026-11-02T10:00", "2026-11-02T14:00"), KEY))
			.hasStatus(HttpStatus.CREATED);

		assertThat(alice.post("/api/shifts", shift(cafe, "2026-11-03T10:00", "2026-11-03T14:00"), KEY))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("This Idempotency-Key was already used for a different request.");
		assertThat(count("shifts")).isEqualTo(1);
	}

	@Test
	@DisplayName("I3: a key used on one endpoint cannot be reused on another")
	void aKeyBelongsToOneEndpoint() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		assertThat(alice.post("/api/shifts", shift(cafe, "2026-11-02T10:00", "2026-11-02T14:00"), KEY))
			.hasStatus(HttpStatus.CREATED);

		assertThat(alice.post("/api/jobs", job("Bookshop", 1100), KEY)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("jobs")).isEqualTo(1);
	}

	@Test
	@DisplayName("I4: keys are per user")
	void keysArePerUser() {
		ApiClient alice = signedUp("alice@example.com");
		ApiClient bob = signedUp("bob@example.com");

		assertThat(alice.post("/api/jobs", job("Cafe", 1200), KEY)).hasStatus(HttpStatus.CREATED);
		assertThat(bob.post("/api/jobs", job("Cafe", 1200), KEY)).hasStatus(HttpStatus.CREATED)
			.doesNotContainHeader("Idempotent-Replayed");
		assertThat(count("jobs")).isEqualTo(2);
	}

	@Test
	@DisplayName("I5: a request that needed confirmation can be confirmed with the same key")
	void failedRequestsAreNotStored() {
		clock.set(jst("2026-11-01T09:00"));
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createDailyShifts(alice, cafe, "2026-11-02", "2026-11-04", "10:00", "17:00");
		String draft = shift(cafe, "2026-11-05T10:00", "2026-11-05T18:00");

		assertThat(alice.post("/api/shifts", draft, KEY)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(alice.post("/api/shifts", draft.replace("}", ", \"confirmed\": true}"), KEY))
			.hasStatus(HttpStatus.CREATED);
		assertThat(count("idempotency_keys")).isEqualTo(1);
	}

	@Test
	@DisplayName("I6: a rejected overlap is not stored, so the key stays free")
	void conflictsAreNotStored() {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		createShift(alice, cafe, "2026-11-02T10:00", "2026-11-02T14:00");

		assertThat(alice.post("/api/shifts", shift(cafe, "2026-11-02T13:00", "2026-11-02T15:00"), KEY))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(count("idempotency_keys")).isZero();
		assertThat(alice.post("/api/shifts", shift(cafe, "2026-11-02T14:00", "2026-11-02T16:00"), KEY))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("I7: an expired key is used again as if new")
	void expiredKeysRunAgain() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/jobs", job("Cafe", 1200), KEY)).hasStatus(HttpStatus.CREATED);
		jdbc.sql("UPDATE idempotency_keys SET expires_at = now() - interval '1 minute'").update();

		assertThat(alice.post("/api/jobs", job("Bookshop", 1100), KEY)).hasStatus(HttpStatus.CREATED)
			.doesNotContainHeader("Idempotent-Replayed");
		assertThat(count("jobs")).isEqualTo(2);
		assertThat(count("idempotency_keys")).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "key with spaces", "ключ" })
	@DisplayName("I8: malformed keys are rejected")
	void malformedKeysAreRejected(String key) {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.post("/api/jobs", job("Cafe", 1200), key)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(alice.post("/api/jobs", job("Cafe", 1200), "k".repeat(256))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(count("jobs")).isZero();
	}

	@Test
	@DisplayName("I9: without a key every request writes")
	void requestsWithoutAKeyAreNotDeduplicated() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.post("/api/jobs", job("Cafe", 1200))).hasStatus(HttpStatus.CREATED);
		assertThat(alice.post("/api/jobs", job("Cafe", 1200))).hasStatus(HttpStatus.CREATED);
		assertThat(count("jobs")).isEqualTo(2);
		assertThat(count("idempotency_keys")).isZero();
	}

	@Test
	@DisplayName("I10: two concurrent requests with the same key write once and answer the same")
	void concurrentDuplicatesWriteOnce() throws Exception {
		ApiClient alice = signedUp("alice@example.com");
		String cafe = createJob(alice, "Cafe", 1200);
		alice.get("/api/health");
		String body = shift(cafe, "2026-11-02T10:00", "2026-11-02T14:00");

		List<MvcTestResult> results = concurrently(() -> alice.copy().post("/api/shifts", body, KEY),
				() -> alice.copy().post("/api/shifts", body, KEY));

		assertThat(results).allSatisfy(result -> assertThat(result).hasStatus(HttpStatus.CREATED));
		assertThat(results.get(0).getResponse().getContentAsString())
			.isEqualTo(results.get(1).getResponse().getContentAsString());
		assertThat(count("shifts")).isEqualTo(1);
	}

	@Test
	@DisplayName("I11: the same body with fields in another order is the same request")
	void fieldOrderDoesNotMatter() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.post("/api/jobs", """
				{"name": "Cafe", "hourlyWageYen": 1200}""", KEY)).hasStatus(HttpStatus.CREATED);
		assertThat(alice.post("/api/jobs", """
				{ "hourlyWageYen": 1200,
				  "name": "Cafe" }""", KEY)).hasStatus(HttpStatus.CREATED).containsHeader("Idempotent-Replayed");
		assertThat(count("jobs")).isEqualTo(1);
	}

	@Test
	@DisplayName("I12: cleanup removes only expired keys")
	void cleanupRemovesExpiredKeys() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/jobs", job("Cafe", 1200), "old")).hasStatus(HttpStatus.CREATED);
		assertThat(alice.post("/api/jobs", job("Bookshop", 1100), "new")).hasStatus(HttpStatus.CREATED);
		jdbc.sql("UPDATE idempotency_keys SET expires_at = now() - interval '1 minute' WHERE idempotency_key = 'old'")
			.update();

		idempotency.removeExpired();

		assertThat(jdbc.sql("SELECT idempotency_key FROM idempotency_keys").query(String.class).list())
			.containsExactly("new");
	}

	@Test
	@DisplayName("I13: deleting the account deletes its keys")
	void accountDeletionRemovesKeys() {
		ApiClient alice = signedUp("alice@example.com");
		assertThat(alice.post("/api/jobs", job("Cafe", 1200), KEY)).hasStatus(HttpStatus.CREATED);

		assertThat(alice.delete("/api/account")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(count("idempotency_keys")).isZero();
	}

	@SafeVarargs
	static <T> List<T> concurrently(Callable<T>... tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = Arrays.stream(tasks).map(task -> executor.submit(() -> {
				start.await();
				return task.call();
			})).toList();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

}
