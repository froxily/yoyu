package com.shift28;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shift28.app.MailService;

public class CapturingMailService implements MailService {

	private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

	private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

	private volatile boolean failing;

	@Override
	public void send(MailMessage message) {
		if (failing) {
			throw new IllegalStateException("SMTP unavailable");
		}
		sent.add(message);
	}

	public List<MailMessage> sent() {
		return List.copyOf(sent);
	}

	public List<MailMessage> sentTo(String to) {
		return sent.stream().filter(message -> message.to().equalsIgnoreCase(to)).toList();
	}

	public String lastCodeFor(String to) {
		List<MailMessage> messages = sentTo(to);
		for (int i = messages.size() - 1; i >= 0; i--) {
			Matcher matcher = CODE.matcher(messages.get(i).body());
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		throw new AssertionError("No code was emailed to " + to);
	}

	public void failing(boolean failing) {
		this.failing = failing;
	}

	public void clear() {
		sent.clear();
		failing = false;
	}

}
