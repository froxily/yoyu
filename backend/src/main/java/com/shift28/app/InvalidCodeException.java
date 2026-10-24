package com.shift28.app;

public class InvalidCodeException extends RuntimeException {

	public InvalidCodeException() {
		super("That code is wrong or has expired.");
	}

}
