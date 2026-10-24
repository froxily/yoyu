package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class PublicOriginTest {

	@Test
	void anHttpsOriginIsAccepted() {
		PublicOrigin origin = new PublicOrigin("https://Yoyu.AzureWebsites.net/");

		assertThat(origin.configured()).isTrue();
		assertThat(origin.url()).isEqualTo("https://yoyu.azurewebsites.net");
		assertThat(origin.host()).isEqualTo("yoyu.azurewebsites.net");
		assertThat(origin.port()).isEqualTo(443);
	}

	@Test
	void aPortOtherThan443IsKept() {
		PublicOrigin origin = new PublicOrigin("https://yoyu.example.test:8443");

		assertThat(origin.url()).isEqualTo("https://yoyu.example.test:8443");
		assertThat(origin.port()).isEqualTo(8443);
	}

	@Test
	void blankMeansNotConfigured() {
		assertThat(new PublicOrigin("").configured()).isFalse();
		assertThat(new PublicOrigin("  ").configured()).isFalse();
	}

	@Test
	void anythingButABareHttpsOriginIsRejected() {
		for (String value : new String[] { "http://yoyu.azurewebsites.net", "yoyu.azurewebsites.net",
				"https://yoyu.azurewebsites.net/app", "https://user@yoyu.azurewebsites.net",
				"https://yoyu.azurewebsites.net?x=1", "https://yoyu.azurewebsites.net#top", "https://",
				"https://yoyu azurewebsites.net" }) {
			assertThatIllegalStateException().as(value).isThrownBy(() -> new PublicOrigin(value));
		}
	}

}
