package com.shift28.persistence;

import static com.shift28.persistence.SqlStates.CHECK;
import static com.shift28.persistence.SqlStates.UNIQUE;
import static com.shift28.persistence.SqlStates.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.shift28.IntegrationTest;
import com.shift28.domain.RuleSet;

@IntegrationTest
class RuleVersionsTest {

	@Autowired
	JdbcClient jdbc;

	@Autowired
	RuleVersionRepository ruleVersions;

	@Test
	@DisplayName("T30: Flyway seeds one rule version equal to the SPEC constants")
	void theSeededVersionIsTheBaseline() {
		assertThat(ruleVersions.findAll()).extracting(RuleVersionEntity::toDomain).containsExactly(RuleSet.BASELINE);
		assertThat(ruleVersions.findAll()).singleElement()
			.satisfies(version -> assertThat(version.getSourceUrl()).startsWith("https://www.moj.go.jp/isa/"));
	}

	@Test
	@DisplayName("T30: two versions cannot take effect on the same date")
	void effectiveDatesAreUnique() {
		assertRejected(UNIQUE, () -> insert("2000-01-01", 1680, 2400, 480));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0, 2400, 480", "1680, 0, 480", "1680, 2400, 0" })
	@DisplayName("T30: every limit must be positive")
	void limitsMustBePositive(String limits) {
		String[] values = limits.split(", ");
		assertRejected(CHECK, () -> insert("2030-01-01", Integer.parseInt(values[0]), Integer.parseInt(values[1]),
				Integer.parseInt(values[2])));
	}

	private void insert(String effectiveFrom, int regular, int vacation, int vacationDay) {
		jdbc.sql("""
				INSERT INTO rule_versions (effective_from, regular_window_limit_minutes, vacation_window_limit_minutes,
				  vacation_day_limit_minutes, source_url) VALUES (?::date, ?, ?, ?, 'https://example.com')""")
			.params(effectiveFrom, regular, vacation, vacationDay)
			.update();
	}

}
