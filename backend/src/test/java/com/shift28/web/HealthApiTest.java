package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

class HealthApiTest extends ApiTestSupport {

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Test
	void theHealthCheckRunsNoQueriesAndLeavesTheSessionAlone() {
		ApiClient alice = signedUp("alice@example.com");
		Cookie session = alice.cookie(ApiClient.SESSION_COOKIE);
		long lastAccess = jdbc.sql("SELECT last_access_time FROM spring_session").query(Long.class).single();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		long statements = statistics.getPrepareStatementCount();

		MvcTestResult health = mvc.get().uri("/api/health").cookie(session).exchange();

		assertThat(health).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"status": "UP"}""");
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(statements);
		assertThat(health.getResponse().getCookie(ApiClient.SESSION_COOKIE)).isNull();
		assertThat(jdbc.sql("SELECT last_access_time FROM spring_session").query(Long.class).single())
			.isEqualTo(lastAccess);
	}

	@Test
	void requestsForwardedOverHttpsGetStrictTransportSecurity() {
		MvcTestResult https = mvc.get().uri("/api/health").header("X-Forwarded-Proto", "https").exchange();
		MvcTestResult http = mvc.get().uri("/api/health").exchange();

		assertThat(https).hasStatusOk();
		assertThat(https.getResponse().getHeader("Strict-Transport-Security")).contains("max-age=");
		assertThat(http.getResponse().getHeader("Strict-Transport-Security")).isNull();
		assertThat(https.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
	}

}
