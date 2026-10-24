package com.shift28;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(properties = { "spring.jpa.properties.hibernate.generate_statistics=true",
		"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn",
		"mail.enabled=true", "mail.async=false", "mail.host=localhost", "mail.from=test@example.test",
		"otp.secret=test-only-otp-secret-0123456789abcdef", "oauth.google.client-id=google-test-client",
		"oauth.google.client-secret=google-test-secret", "oauth.line.channel-id=1234567890",
		"oauth.line.channel-secret=line-test-channel-secret-0123456789" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, TestMockMvcConfiguration.class,
		TestMailConfiguration.class })
public @interface IntegrationTest {

}
