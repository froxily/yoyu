package com.shift28.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.dao.DataIntegrityViolationException;

final class SqlStates {

	static final String NOT_NULL = "23502";
	static final String FOREIGN_KEY = "23503";
	static final String UNIQUE = "23505";
	static final String CHECK = "23514";
	static final String EXCLUSION = "23P01";

	private SqlStates() {
	}

	static void assertRejected(String sqlState, ThrowingCallable statement) {
		assertThatThrownBy(statement).isInstanceOf(DataIntegrityViolationException.class)
			.extracting(SqlStates::sqlState)
			.isEqualTo(sqlState);
	}

	private static String sqlState(Throwable thrown) {
		for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql) {
				return sql.getSQLState();
			}
		}
		return null;
	}

}
