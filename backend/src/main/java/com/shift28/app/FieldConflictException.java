package com.shift28.app;

public class FieldConflictException extends ConflictException {

	private final String field;

	public FieldConflictException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}

}
