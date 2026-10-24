package com.shift28.app;

import java.util.UUID;

public class VerificationRequiredException extends RuntimeException {

	private final UUID verificationId;

	public VerificationRequiredException(UUID verificationId) {
		super("Enter the code we emailed you to finish signing in.");
		this.verificationId = verificationId;
	}

	public UUID verificationId() {
		return verificationId;
	}

}
