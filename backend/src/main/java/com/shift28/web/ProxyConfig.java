package com.shift28.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.DispatcherType;

@Configuration(proxyBeanMethods = false)
class ProxyConfig {

	@Bean
	FilterRegistrationBean<OncePerRequestFilter> forwardedHeaderFilter(PublicOrigin origin) {
		FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(
				origin.configured() ? new PublicOriginFilter(origin) : new ForwardedHeaderFilter());
		registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

}
