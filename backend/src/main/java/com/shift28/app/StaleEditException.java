package com.shift28.app;

public class StaleEditException extends ConflictException {

	public StaleEditException(String message) {
		super(message);
	}

}
