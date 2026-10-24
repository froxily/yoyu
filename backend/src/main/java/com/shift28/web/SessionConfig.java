package com.shift28.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration(proxyBeanMethods = false)
class SessionConfig {

	@Bean
	DefaultCookieSerializerCustomizer sessionCookie(@Value("${session.signed-in-timeout}") Duration signedInTimeout,
			@Value("${server.servlet.session.cookie.secure}") boolean secure, PublicOrigin origin) {
		return serializer -> {
			serializer.setCookieMaxAge(Math.toIntExact(signedInTimeout.toSeconds()));
			serializer.setUseHttpOnlyCookie(true);
			serializer.setUseSecureCookie(secure || origin.configured());
			serializer.setSameSite("Lax");
		};
	}

	@Bean(DispatcherServlet.FLASH_MAP_MANAGER_BEAN_NAME)
	FlashMapManager flashMapManager() {
		return new NoFlashMaps();
	}

	static final class NoFlashMaps implements FlashMapManager {

		@Override
		public FlashMap retrieveAndUpdate(HttpServletRequest request, HttpServletResponse response) {
			return null;
		}

		@Override
		public void saveOutputFlashMap(FlashMap flashMap, HttpServletRequest request, HttpServletResponse response) {
		}

	}

}
