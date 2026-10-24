package com.shift28.web;

import static com.shift28.web.LoginApiTest.login;
import static com.shift28.web.SocialSignInApiTest.callback;
import static com.shift28.web.SocialSignInApiTest.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.session.autoconfigure.SessionProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.shift28.MutableClock;

import jakarta.servlet.http.Cookie;

class AnonymousSessionApiTest extends ApiTestSupport {

	private static final int TEN_MINUTES = (int) Duration.ofMinutes(10).toSeconds();

	private static final int THIRTY_DAYS = (int) Duration.ofDays(30).toSeconds();

	@Autowired
	SessionCleanup cleanup;

	@Autowired
	SessionProperties sessionProperties;

	@Autowired
	ConfigurableApplicationContext context;

	@Autowired
	ScheduledTaskHolder scheduledTasks;

	@Autowired
	FakeTokenEndpoint tokens;

	@Autowired
	DataSource dataSource;

	@Test
	void anOAuthStartWithoutAnAccountCreatesASessionThatLastsTenMinutes() {
		MvcTestResult start = startFrom("203.0.113.1");

		assertThat(start).hasStatus(HttpStatus.FOUND);
		assertThat(start.getResponse().getRedirectedUrl()).startsWith("https://accounts.google.com/");
		assertThat(anonymousIntervals()).containsExactly(TEN_MINUTES);
	}

	@Test
	void signingInWithAPasswordAfterAnOAuthStartLastsThirtyDays() {
		String id = userId(signedUp("alice@example.com"));
		ApiClient client = new ApiClient(mvc);
		assertThat(client.get("/oauth2/authorization/google")).hasStatus(HttpStatus.FOUND);
		assertThat(anonymousIntervals()).containsExactly(TEN_MINUTES);

		assertThat(client.post("/api/auth/login", login("alice@example.com", PASSWORD))).hasStatusOk();

		assertThat(anonymousIntervals()).isEmpty();
		assertThat(intervalsOf(id)).hasSize(2).containsOnly(THIRTY_DAYS);
	}

	@Test
	void signingUpAndSigningInWithGoogleLastThirtyDays() {
		ApiClient owner = new ApiClient(mvc);
		assertThat(callback(owner, "google", identity("google-session", null, null, clock.instant())))
			.isEqualTo("/welcome");
		assertThat(anonymousIntervals()).containsExactly(TEN_MINUTES);
		assertThat(owner.post("/api/auth/social/complete", """
				{"fullName": "Google User", "username": "google_user"}""")).hasStatus(HttpStatus.CREATED);
		String id = userId(owner);
		ApiClient device = new ApiClient(mvc);

		assertThat(callback(device, "google", identity("google-session", null, null, clock.instant()))).isEqualTo("/");

		assertThat(anonymousIntervals()).isEmpty();
		assertThat(intervalsOf(id)).hasSize(2).containsOnly(THIRTY_DAYS);
	}

	@Test
	void oneClientCanStartTwentyOAuthSignInsPerFifteenMinutes() {
		for (int start = 1; start <= 20; start++) {
			assertThat(startFrom("203.0.113.10").getResponse().getRedirectedUrl()).as("start %d", start)
				.startsWith("https://accounts.google.com/");
		}

		assertThat(startFrom("203.0.113.10").getResponse().getRedirectedUrl()).isEqualTo("/login?error=rate-limited");
		assertThat(anonymousIntervals()).hasSize(20);
	}

	@Test
	void allClientsTogetherCanStartAtMostThreeHundredNewSessionsPerFifteenMinutes() {
		for (int client = 0; client < 300; client++) {
			assertThat(startFrom(address(client)).getResponse().getRedirectedUrl()).as("client %d", client)
				.startsWith("https://accounts.google.com/");
		}

		assertThat(startFrom("10.9.9.9").getResponse().getRedirectedUrl()).isEqualTo("/login?error=rate-limited");
		assertThat(anonymousIntervals()).hasSize(300);
	}

	@Test
	void aSignedInAccountCanStillLinkWhileNewSessionsAreCapped() {
		ApiClient alice = signedUp("alice@example.com");
		for (int client = 0; client < 300; client++) {
			startFrom(address(client));
		}
		assertThat(alice.post("/api/account/identities/line/link", "")).hasStatusOk();

		MvcTestResult start = alice.get("/oauth2/authorization/line");

		assertThat(start.getResponse().getRedirectedUrl()).startsWith("https://access.line.me/");
	}

	@Test
	void aSignedInAccountOverItsClientLimitIsSentBackToSettings() {
		ApiClient alice = signedUp("alice@example.com");
		for (int start = 0; start < 20; start++) {
			assertThat(alice.get("/oauth2/authorization/google")).hasStatus(HttpStatus.FOUND);
		}

		assertThat(alice.get("/oauth2/authorization/google").getResponse().getRedirectedUrl())
			.isEqualTo("/settings?error=rate-limited");
	}

	@Test
	void failedSignInsCreateNoSessions() {
		ApiClient client = new ApiClient(mvc);
		for (int attempt = 0; attempt < 3; attempt++) {
			assertThat(client.post("/api/auth/login", login("nobody_here", PASSWORD))).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		assertThat(count("spring_session")).isZero();
	}

	@Test
	void staleAnonymousSessionsAreRemovedOnStartupWhileSignedInOnesStay() {
		ApiClient alice = signedUp("alice@example.com");
		startFrom("203.0.113.20");
		startFrom("203.0.113.21");
		jdbc.sql("UPDATE spring_session SET max_inactive_interval = ?, expiry_time = last_access_time + ?"
				+ " WHERE session_id = (SELECT max(session_id) FROM spring_session WHERE principal_name IS NULL)")
			.params(THIRTY_DAYS, Duration.ofDays(30).toMillis())
			.update();
		ageSessions(Duration.ofMinutes(11));

		cleanup.removeStaleOnStartup();

		assertThat(anonymousIntervals()).isEmpty();
		assertThat(jdbc.sql("SELECT count(*) FROM spring_session_attributes a WHERE NOT EXISTS"
				+ " (SELECT 1 FROM spring_session s WHERE s.primary_id = a.session_primary_id)").query(Integer.class)
			.single()).isZero();
		assertThat(alice.get("/api/auth/me")).hasStatusOk();
	}

	@Test
	void anIdleAppNeverSweeps() {
		SessionCleanup idle = new SessionCleanup(jdbc, sessionProperties);
		startFrom("203.0.113.30");
		ageSessions(Duration.ofMinutes(11));

		idle.sweepWhileAnonymousSessionsMayExist();

		assertThat(anonymousIntervals()).hasSize(1);
	}

	@Test
	void afterAnAnonymousStartTheSweepKeepsRemovingStaleSessionsUntilTheyCanAllHaveExpired() {
		SessionCleanup sweeper = new SessionCleanup(jdbc, sessionProperties);
		startFrom("203.0.113.31");
		sweeper.anonymousSessionStarted();
		sweeper.sweepWhileAnonymousSessionsMayExist();
		assertThat(anonymousIntervals()).hasSize(1);
		ageSessions(Duration.ofMinutes(11));

		sweeper.sweepWhileAnonymousSessionsMayExist();
		assertThat(anonymousIntervals()).isEmpty();

		startFrom("203.0.113.32");
		ageSessions(Duration.ofMinutes(11));
		sweeper.sweepWhileAnonymousSessionsMayExist();
		assertThat(anonymousIntervals()).isEmpty();
	}

	@Test
	void aRestartKeepsSweepingAnonymousSessionsThatSurviveStartup() {
		startFrom("203.0.113.35");
		MutableClock now = new MutableClock(Instant.now());
		SessionCleanup restarted = new SessionCleanup(jdbc, sessionProperties, now);
		restarted.removeStaleOnStartup();
		assertThat(anonymousIntervals()).hasSize(1);
		now.set(now.instant().plus(Duration.ofMinutes(31)));
		touchAnonymousSessionsAt(now.millis());

		restarted.sweepWhileAnonymousSessionsMayExist();

		assertThat(anonymousIntervals()).isEmpty();
	}

	@Test
	void theObligationEndsOnlyAfterASuccessfulSweepPastTheNewestSessionsMaximumAge() {
		FlakyDataSource flaky = new FlakyDataSource(dataSource);
		MutableClock now = new MutableClock(Instant.now());
		SessionCleanup sweeper = new SessionCleanup(JdbcClient.create(flaky), sessionProperties, now);
		startFrom("203.0.113.36");
		sweeper.anonymousSessionStarted();
		now.set(now.instant().plus(Duration.ofMinutes(31)));
		touchAnonymousSessionsAt(now.millis());

		flaky.failing = true;
		assertThatThrownBy(sweeper::sweepWhileAnonymousSessionsMayExist).isInstanceOf(DataAccessException.class);
		flaky.failing = false;
		sweeper.sweepWhileAnonymousSessionsMayExist();
		assertThat(anonymousIntervals()).isEmpty();

		startFrom("203.0.113.37");
		touchAnonymousSessionsAt(now.millis());
		sweeper.sweepWhileAnonymousSessionsMayExist();
		assertThat(anonymousIntervals()).hasSize(1);
	}

	@Test
	void anAnonymousSessionKeptAliveIsRemovedOnceItIsThirtyMinutesOld() {
		Cookie session = startFrom("203.0.113.33").getResponse().getCookie(ApiClient.SESSION_COOKIE);
		assertThat(mvc.get().uri("/api/auth/social/pending").cookie(session)).hasStatus(HttpStatus.NOT_FOUND);
		jdbc.sql("UPDATE spring_session SET creation_time = creation_time - ? WHERE principal_name IS NULL")
			.param(Duration.ofMinutes(31).toMillis())
			.update();
		assertThat(mvc.get().uri("/api/auth/social/pending").cookie(session)).hasStatus(HttpStatus.NOT_FOUND);

		cleanup.removeStaleOnStartup();

		assertThat(anonymousIntervals()).isEmpty();
	}

	@Test
	void agedSignedInSessionsAreKept() {
		ApiClient alice = signedUp("alice@example.com");
		jdbc.sql("UPDATE spring_session SET creation_time = creation_time - ?, last_access_time = last_access_time - ?")
			.params(Duration.ofDays(2).toMillis(), Duration.ofHours(1).toMillis())
			.update();

		cleanup.removeStaleOnStartup();

		assertThat(count("spring_session")).isOne();
		assertThat(alice.get("/api/auth/me")).hasStatusOk();
	}

	@Test
	void aSignedInSessionWhosePrincipalColumnWasLostIsKept() {
		ApiClient alice = signedUp("alice@example.com");
		jdbc.sql("UPDATE spring_session SET principal_name = NULL, creation_time = creation_time - ?,"
				+ " last_access_time = last_access_time - ?")
			.params(Duration.ofHours(1).toMillis(), Duration.ofHours(1).toMillis())
			.update();

		cleanup.removeStaleOnStartup();

		assertThat(count("spring_session")).isOne();
		assertThat(alice.get("/api/auth/me")).hasStatusOk();
	}

	@Test
	void aForgedSessionCookieAtTheCapIsStillLimited() {
		for (int client = 0; client < 300; client++) {
			startFrom(address(client));
		}

		MvcTestResult forged = mvc.get().uri("/oauth2/authorization/google")
			.cookie(new Cookie(ApiClient.SESSION_COOKIE, "bm90LWEtcmVhbC1zZXNzaW9u"))
			.with(request -> {
				request.setRemoteAddr("10.9.9.10");
				return request;
			})
			.exchange();

		assertThat(forged.getResponse().getRedirectedUrl()).isEqualTo("/login?error=rate-limited");
		assertThat(anonymousIntervals()).hasSize(300);
	}

	@Test
	void unusualStartPathsCannotCreateMoreSessionsThanTheClientLimit() {
		String[] paths = { "/oauth2/authorization/%67oogle", "/oauth2/authorization/google/",
				"/oauth2/authorization/google;x=1", "/oauth2/authorization/google?x=1" };
		for (int variant = 0; variant < paths.length; variant++) {
			int before = anonymousIntervals().size();
			String ip = "198.51.100." + variant;
			for (int attempt = 0; attempt < 25; attempt++) {
				mvc.get().uri(paths[variant]).with(request -> {
					request.setRemoteAddr(ip);
					return request;
				}).exchange();
			}
			assertThat(anonymousIntervals().size() - before).as(paths[variant]).isLessThanOrEqualTo(20);
		}
	}

	@Test
	void staleSessionsAreRemovedWhenTheApplicationIsReady() {
		startFrom("203.0.113.34");
		ageSessions(Duration.ofMinutes(11));

		context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

		assertThat(anonymousIntervals()).isEmpty();
	}

	@Test
	void theSweepIsScheduledEveryTenMinutes() {
		assertThat(scheduledTasks.getScheduledTasks()).anySatisfy(task -> {
			assertThat(task.getTask().toString()).contains("SessionCleanup.sweepWhileAnonymousSessionsMayExist");
			assertThat(task.getTask()).isInstanceOfSatisfying(IntervalTask.class,
					interval -> assertThat(interval.getIntervalDuration()).isEqualTo(Duration.ofMinutes(10)));
		});
	}

	@Test
	void signingInThroughTheRealLineCallbackLastsThirtyDays() {
		ApiClient alice = signedUp("alice@example.com");
		String id = userId(alice);
		assertThat(alice.post("/api/account/identities/line/link", "")).hasStatusOk();
		assertThat(signInWithLine(alice, "session-line")).isEqualTo("/settings?linked=line");
		ApiClient device = new ApiClient(mvc);

		assertThat(signInWithLine(device, "session-line")).isEqualTo("/");

		assertThat(anonymousIntervals()).isEmpty();
		assertThat(intervalsOf(id)).hasSize(2).containsOnly(THIRTY_DAYS);
	}

	private String signInWithLine(ApiClient client, String subject) {
		MvcTestResult start = client.get("/oauth2/authorization/line");
		assertThat(start).hasStatus(HttpStatus.FOUND);
		String state = UriComponentsBuilder.fromUriString(start.getResponse().getHeader("Location"))
			.build()
			.getQueryParams()
			.getFirst("state");
		tokens.willIssue(Map.of("sub", subject, "auth_time", clock.instant().getEpochSecond(), "amr", List.of("pwd")));

		MvcTestResult callback = client.get("/login/oauth2/code/line?code=test-code&state="
				+ URLDecoder.decode(state, StandardCharsets.UTF_8));

		assertThat(callback).hasStatus(HttpStatus.FOUND);
		return callback.getResponse().getRedirectedUrl();
	}

	private MvcTestResult startFrom(String ip) {
		return mvc.get().uri("/oauth2/authorization/google").with(request -> {
			request.setRemoteAddr(ip);
			return request;
		}).exchange();
	}

	private static String address(int client) {
		return "10.0." + client / 256 + "." + client % 256;
	}

	private void touchAnonymousSessionsAt(long millis) {
		jdbc.sql("UPDATE spring_session SET last_access_time = ?, expiry_time = ? + max_inactive_interval * 1000"
				+ " WHERE principal_name IS NULL")
			.params(millis, millis)
			.update();
	}

	private static final class FlakyDataSource extends DelegatingDataSource {

		volatile boolean failing;

		FlakyDataSource(DataSource target) {
			super(target);
		}

		@Override
		public Connection getConnection() throws SQLException {
			if (failing) {
				throw new SQLException("database unavailable");
			}
			return super.getConnection();
		}

	}

	private void ageSessions(Duration age) {
		jdbc.sql("UPDATE spring_session SET last_access_time = last_access_time - ?, expiry_time = expiry_time - ?"
				+ " WHERE principal_name IS NULL")
			.params(age.toMillis(), age.toMillis())
			.update();
	}

	private List<Integer> anonymousIntervals() {
		return jdbc.sql("SELECT max_inactive_interval FROM spring_session WHERE principal_name IS NULL")
			.query(Integer.class)
			.list();
	}

	private List<Integer> intervalsOf(String userId) {
		return jdbc.sql("SELECT max_inactive_interval FROM spring_session WHERE principal_name = ?")
			.param(userId)
			.query(Integer.class)
			.list();
	}

	private String userId(ApiClient client) {
		return ApiClient.read(client.get("/api/auth/me"), "$.id");
	}

}
