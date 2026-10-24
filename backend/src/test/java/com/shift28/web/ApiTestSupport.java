package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.shift28.CapturingMailService;
import com.shift28.IntegrationTest;
import com.shift28.MutableClock;
import com.shift28.TestClockConfiguration;
import com.shift28.app.RateLimiter;

import jakarta.servlet.http.Cookie;

@IntegrationTest
abstract class ApiTestSupport {

	static final String PASSWORD = "correct horse battery";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	MutableClock clock;

	@Autowired
	CapturingMailService mail;

	@Autowired
	RateLimiter limiter;

	@BeforeEach
	void resetDatabaseAndClock() {
		jdbc.sql("TRUNCATE users, spring_session, email_codes CASCADE").update();
		clock.set(TestClockConfiguration.DEFAULT_NOW);
		limiter.clear();
		mail.clear();
	}

	ApiClient signedUp(String email) {
		ApiClient client = new ApiClient(mvc);
		String verificationId = startRegistration(client, email, usernameFor(email));
		assertThat(client.post("/api/auth/register/verify", code(verificationId, mail.lastCodeFor(email))))
			.hasStatus(HttpStatus.CREATED);
		return client;
	}

	String startRegistration(ApiClient client, String email, String username) {
		MvcTestResult started = client.post("/api/auth/register", registration(email, username, PASSWORD));
		assertThat(started).hasStatus(HttpStatus.ACCEPTED);
		return ApiClient.read(started, "$.verificationId");
	}

	static String usernameFor(String email) {
		String local = email.substring(0, email.indexOf('@')).toLowerCase().replaceAll("[^a-z0-9_]", "_");
		return (local + "___").substring(0, Math.max(3, Math.min(20, local.length())));
	}

	static String registration(String email, String username, String password) {
		return """
				{"fullName": "Test User", "username": "%s", "email": "%s", "password": "%s"}"""
			.formatted(username, email, password);
	}

	static String code(String verificationId, String code) {
		return """
				{"verificationId": "%s", "code": "%s"}""".formatted(verificationId, code);
	}

	static String createdId(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return ApiClient.read(result, "$.id");
	}

	static String createJob(ApiClient client, String name, int hourlyWageYen) {
		return createdId(client.post("/api/jobs", job(name, hourlyWageYen)));
	}

	static String createShift(ApiClient client, String jobId, String start, String end) {
		MvcTestResult result = client.post("/api/shifts", shift(jobId, start, end));
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return ApiClient.read(result, "$.shift.id");
	}

	static void createDailyShifts(ApiClient client, String jobId, String firstDate, String lastDate, String start,
			String end) {
		LocalDate.parse(firstDate)
			.datesUntil(LocalDate.parse(lastDate).plusDays(1))
			.forEach(day -> createShift(client, jobId, day + "T" + start, day + "T" + end));
	}

	static String credentials(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}""".formatted(email, password);
	}

	static String job(String name, int hourlyWageYen) {
		return """
				{"name": "%s", "hourlyWageYen": %d}""".formatted(name, hourlyWageYen);
	}

	static String shift(String jobId, String start, String end) {
		return """
				{"jobId": "%s", "startAt": "%s", "endAt": "%s"}""".formatted(jobId, time(start), time(end));
	}

	static String shift(String jobId, String start, String end, String breakStart, String breakEnd) {
		return """
				{"jobId": "%s", "startAt": "%s", "endAt": "%s", "breakStart": "%s", "breakEnd": "%s"}"""
			.formatted(jobId, time(start), time(end), time(breakStart), time(breakEnd));
	}

	static String versioned(String json, long version) {
		return json.substring(0, json.lastIndexOf('}')) + ", \"version\": " + version + "}";
	}

	static String time(String localDateTime) {
		return localDateTime + ":00+09:00";
	}

	static Instant jst(String localDateTime) {
		return OffsetDateTime.parse(time(localDateTime)).toInstant();
	}

	static Map<String, Object> contribution(String shiftId, String jobId, String jobName, String startAt, int minutes,
			boolean draft) {
		Map<String, Object> contribution = new LinkedHashMap<>();
		if (shiftId != null) {
			contribution.put("shiftId", shiftId);
		}
		contribution.put("jobId", jobId);
		contribution.put("jobName", jobName);
		contribution.put("startAt", startAt);
		contribution.put("minutes", minutes);
		contribution.put("draft", draft);
		return contribution;
	}

	List<String> shiftIdsInStartOrder() {
		return jdbc.sql("SELECT id::text FROM shifts ORDER BY start_at").query(String.class).list();
	}

	long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

	long storedSessions(Cookie sessionCookie) {
		String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
		return jdbc.sql("SELECT count(*) FROM spring_session WHERE session_id = ?")
			.param(sessionId)
			.query(Long.class)
			.single();
	}

}
