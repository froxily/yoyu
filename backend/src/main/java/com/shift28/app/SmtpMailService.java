package com.shift28.app;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

final class SmtpMailService implements MailService {

	private final JavaMailSender sender;

	private final String from;

	SmtpMailService(JavaMailSender sender, String from) {
		this.sender = sender;
		this.from = from;
	}

	@Override
	public void send(MailMessage message) {
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(from);
		mail.setTo(message.to());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		sender.send(mail);
	}

}
