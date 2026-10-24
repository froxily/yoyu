package com.shift28.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpServletRequestWrapper;

class ClientIpResolverTest {

	@Test
	void configuredHeaderUsesTheLastAddressTheProxyAppended() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.0.2.10");
		request.addHeader("X-Client-IP", " 198.51.100.10 , 198.51.100.11");
		request.addHeader("X-Client-IP", "198.51.100.12");

		assertThat(new ClientIpResolver("X-Client-IP").resolve(request)).isEqualTo("198.51.100.12");
	}

	@Test
	void forgedValuesBeforeTheAppendedAddressAreIgnored() {
		for (String forged : new String[] { "203.0.113.7, 198.51.100.20", "not-an-ip<script>, 198.51.100.20",
				"203.0.113.7,, 198.51.100.20", ", 198.51.100.20" }) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("192.0.2.10");
			request.addHeader("X-Client-IP", forged);

			assertThat(new ClientIpResolver("X-Client-IP").resolve(request)).as(forged).isEqualTo("198.51.100.20");
		}
	}

	@Test
	void aLastValueThatIsNotAnAddressFallsBackToTheSocketAddress() {
		for (String value : new String[] { "198.51.100.10, not-an-ip", "198.51.100.10,", "", "999.1.1.1",
				"198.51.100.10 198.51.100.11", "x".repeat(100) }) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("192.0.2.10");
			request.addHeader("X-Client-IP", value);

			assertThat(new ClientIpResolver("X-Client-IP").resolve(request)).as(value).isEqualTo("192.0.2.10");
		}
	}

	@Test
	void ipv6AddressesAreAccepted() {
		for (String address : new String[] { "2001:db8::1", "::1", "::", "2001:db8:0:0:0:0:0:1", "::ffff:192.0.2.1",
				"fe80::1:2:3:4" }) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("192.0.2.10");
			request.addHeader("X-Client-IP", "198.51.100.10, " + address);

			assertThat(new ClientIpResolver("X-Client-IP").resolve(request)).as(address).isEqualTo(address);
		}
	}

	@Test
	void addressesWithPortsAndInvalidIpv6FallBackToTheSocketAddress() {
		for (String value : new String[] { "198.51.100.20:54321", ":::", "1:2", "[2001:db8::1]", "2001:db8::1%eth0",
				"1:2:3:4:5:6:7:8:9", "1::2::3", "12345::1", "::ffff:999.0.2.1", "::192.0.2.1:1" }) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("192.0.2.10");
			request.addHeader("X-Client-IP", value);

			assertThat(new ClientIpResolver("X-Client-IP").resolve(request)).as(value).isEqualTo("192.0.2.10");
		}
	}

	@Test
	void defaultConfigurationIgnoresHeadersAndUnwrapsTheOriginalSocketAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.0.2.10");
		request.addHeader("X-Client-IP", "198.51.100.10");
		request.addHeader("X-Forwarded-For", "198.51.100.11");
		HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
			@Override
			public String getRemoteAddr() {
				return "203.0.113.10";
			}
		};

		assertThat(new ClientIpResolver("").resolve(wrapped)).isEqualTo("192.0.2.10");
	}

}
