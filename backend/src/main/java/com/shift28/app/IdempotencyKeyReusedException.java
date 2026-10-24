package com.shift28.app;

public class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException() {
		super("This Idempotency-Key was already used for a different request.");
	}

}
