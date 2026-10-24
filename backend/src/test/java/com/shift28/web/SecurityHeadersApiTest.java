package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SecurityHeadersApiTest extends ApiTestSupport {

	@Test
	void healthResponsesIncludeTheConfiguredSecurityHeaders() {
		MvcTestResult result = new ApiClient(mvc).get("/api/health");

		assertThat(result).hasStatusOk();
		MockHttpServletResponse response = result.getResponse();
		assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(SecurityConfig.CONTENT_SECURITY_POLICY);
		assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
		assertThat(response.getHeader("Permissions-Policy")).isNotBlank();
		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
	}

}
