package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ErrorHandlingApiTest extends ApiTestSupport {

	@Test
	void unexpectedErrorsAreA500WithoutDetails() throws Exception {
		ApiClient alice = signedUp("alice@example.com");

		MvcTestResult failure = alice.get("/api/test/failure");

		assertThat(failure).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Something went wrong.");
		assertThat(failure.getResponse().getContentAsString()).doesNotContain("secret")
			.doesNotContain("jdbc")
			.doesNotContain("IllegalStateException")
			.doesNotContain("trace");
	}

	@Test
	void malformedJsonIsA400ProblemDetail() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.post("/api/jobs", "{not json")).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void unknownApiPathsAreA404ProblemDetail() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.get("/api/nothing-here")).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void malformedIdsAreA400() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.delete("/api/jobs/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

}
