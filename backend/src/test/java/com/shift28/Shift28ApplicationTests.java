package com.shift28;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class Shift28ApplicationTests {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	Flyway flyway;

	@Test
	void healthEndpointReportsUp() {
		assertThat(mvc.get().uri("/api/health"))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
	}

	@Test
	void flywayAppliesEveryMigration() {
		assertThat(flyway.info().applied())
			.extracting(MigrationInfo::getScript)
			.containsExactly("V0__baseline.sql", "V1__schema.sql", "V2__job_ownership_and_updated_at.sql",
					"V3__spring_session.sql", "V4__rule_versions.sql", "V5__idempotency_keys.sql",
					"V6__row_versions.sql", "V7__accounts_identities_codes.sql", "V8__session_version.sql");
	}

}
