package com.shift28.app;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shift28.app.MailService.MailMessage;

@Component
public class Mailer {

	private static final Logger log = LoggerFactory.getLogger(Mailer.class);

	private final MailService mail;

	private final Executor executor;

	private final boolean enabled;

	private final String productName;

	Mailer(MailService mail, @Qualifier("mailExecutor") Executor executor, @Value("${mail.enabled:false}") boolean enabled,
			@Value("${mail.product-name}") String productName) {
		this.mail = mail;
		this.executor = executor;
		this.enabled = enabled;
		this.productName = productName;
	}

	public boolean enabled() {
		return enabled;
	}

	public void requireEnabled(String unavailable) {
		if (!enabled) {
			throw new ServiceUnavailableException(unavailable);
		}
	}

	public void sendLater(String to, String subject, String body) {
		MailMessage message = message(to, subject, body);
		Runnable send = () -> {
			try {
				mail.send(message);
			}
			catch (RuntimeException e) {
				log.warn("Email delivery failed: {}", e.getClass().getSimpleName());
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

				@Override
				public void afterCommit() {
					executor.execute(send);
				}

			});
		}
		else {
			executor.execute(send);
		}
	}

	public void sendNow(String to, String subject, String body) {
		try {
			mail.send(message(to, subject, body));
		}
		catch (RuntimeException e) {
			log.warn("Email delivery failed: {}", e.getClass().getSimpleName());
			throw new ServiceUnavailableException("We couldn't send the code. Try again in a moment.");
		}
	}

	public String productName() {
		return productName;
	}

	private MailMessage message(String to, String subject, String body) {
		return new MailMessage(to, productName + ": " + subject, body + "\n\n— " + productName);
	}

}
