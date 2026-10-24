package com.shift28.app;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
class MailConfig {

	private static final String TIMEOUT_MILLIS = "10000";

	@Bean
	MailService mailService(@Value("${mail.enabled:false}") boolean enabled, @Value("${mail.host:}") String host,
			@Value("${mail.port:587}") int port, @Value("${mail.username:}") String username,
			@Value("${mail.password:}") String password, @Value("${mail.starttls:true}") boolean starttls,
			@Value("${mail.from:}") String from) {
		if (enabled && (!StringUtils.hasText(host) || !(StringUtils.hasText(from) || StringUtils.hasText(username)))) {
			throw new IllegalStateException("MAIL_HOST and MAIL_FROM (or MAIL_USERNAME) must be set when mail is enabled");
		}
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(StringUtils.hasText(host) ? host : "localhost");
		sender.setPort(port);
		sender.setDefaultEncoding("UTF-8");
		Properties properties = sender.getJavaMailProperties();
		properties.put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
		properties.put("mail.smtp.timeout", TIMEOUT_MILLIS);
		properties.put("mail.smtp.writetimeout", TIMEOUT_MILLIS);
		if (StringUtils.hasText(username)) {
			sender.setUsername(username);
			sender.setPassword(password);
			properties.put("mail.smtp.auth", "true");
		}
		if (starttls) {
			properties.put("mail.smtp.starttls.enable", "true");
			properties.put("mail.smtp.starttls.required", "true");
		}
		return new SmtpMailService(sender, StringUtils.hasText(from) ? from : username);
	}

	@Bean
	Executor mailExecutor(@Value("${mail.async:true}") boolean async) {
		if (!async) {
			return Runnable::run;
		}
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(50),
				runnable -> {
					Thread thread = new Thread(runnable, "mail");
					thread.setDaemon(true);
					return thread;
				}, new ThreadPoolExecutor.DiscardOldestPolicy());
	}

}
