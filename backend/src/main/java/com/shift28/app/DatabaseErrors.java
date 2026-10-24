package com.shift28.app;

import java.sql.SQLException;

final class DatabaseErrors {

	static final String UNIQUE_VIOLATION = "23505";

	static final String EXCLUSION_VIOLATION = "23P01";

	private DatabaseErrors() {
	}

	static boolean violates(Throwable thrown, String constraint) {
		for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())
					&& String.valueOf(sql.getMessage()).contains(constraint)) {
				return true;
			}
		}
		return false;
	}

	static boolean hasSqlState(Throwable thrown, String sqlState) {
		for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}

}
