package com.shift28.app;

public interface MailService {

	void send(MailMessage message);

	record MailMessage(String to, String subject, String body) {

		@Override
		public String toString() {
			return "MailMessage[subject=" + subject + "]";
		}

	}

}
