package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SpaRoutingApiTest extends ApiTestSupport {

	@Test
	void clientRoutesAreServedTheAppShellWithoutCaching() {
		assertThat(mvc.get().uri("/").exchange()).hasStatusOk().hasForwardedUrl("index.html");
		for (String path : new String[] { "/index.html", "/history", "/shifts/0b0e3e1c-63a5-4c39-9d4b-7d0f2b4c1a11/edit?date=2026-11-02",
				"/jobs?welcome" }) {
			MvcTestResult result = mvc.get().uri(path).exchange();

			assertThat(result).as(path).hasStatusOk().hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
			assertThat(result).as(path).bodyText().contains("spa-fixture");
			assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).as(path).isEqualTo("no-cache");
		}
	}

	@Test
	void hashedAssetsAreCachedForAYear() {
		MvcTestResult result = mvc.get().uri("/assets/fixture-abc123.js").exchange();

		assertThat(result).hasStatusOk().bodyText().contains("fixture");
		assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
			.isEqualTo("max-age=31536000, public, immutable");
	}

	@Test
	void missingFilesAreNotFoundInsteadOfTheAppShell() {
		assertThat(mvc.get().uri("/missing.js").exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/assets/missing-123.css").exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void unknownApiPathsAreProblemDetailsNotTheAppShell() {
		ApiClient alice = signedUp("alice@example.com");

		assertThat(alice.get("/api/nope")).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void wellKnownFilesAreServedWhenPresentAndNeverFallBackToTheAppShell() {
		assertThat(mvc.get().uri("/.well-known/assetlinks.json").exchange()).hasStatusOk()
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.bodyText()
			.isEqualToIgnoringNewLines("[]");
		assertThat(mvc.get().uri("/.well-known/nope").exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void onlyGetRequestsAreForwardedToTheAppShell() {
		ApiClient alice = signedUp("alice@example.com");

		MvcTestResult result = alice.post("/history", "{}");

		assertThat(result).hasStatus4xxClientError().bodyText().doesNotContain("spa-fixture");
	}

}
