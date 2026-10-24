package com.shift28.app;

public class ReauthenticationRequiredException extends RuntimeException {

	public ReauthenticationRequiredException() {
		super("Confirm it's you to continue.");
	}

}
