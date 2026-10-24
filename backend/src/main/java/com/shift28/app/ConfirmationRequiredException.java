package com.shift28.app;

public class ConfirmationRequiredException extends RuntimeException {

	private final DraftCheck check;

	public ConfirmationRequiredException(DraftCheck check) {
		super("This future shift is not within the limits. Confirm to save it anyway.");
		this.check = check;
	}

	public DraftCheck check() {
		return check;
	}

}
