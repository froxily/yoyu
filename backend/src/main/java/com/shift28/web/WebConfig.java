package com.shift28.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

	private static final String STATIC = "classpath:/static/";

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/assets/**")
			.addResourceLocations(STATIC + "assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
		registry.addResourceHandler("/**")
			.addResourceLocations(STATIC)
			.setCacheControl(CacheControl.noCache())
			.resourceChain(true)
			.addResolver(new AppShellResolver());
	}

	static final class AppShellResolver extends PathResourceResolver {

		private static final String APP_SHELL = "index.html";

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource requested = super.getResource(resourcePath, location);
			if (requested != null || !isClientRoute(resourcePath)) {
				return requested;
			}
			return super.getResource(APP_SHELL, location);
		}

		static boolean isClientRoute(String path) {
			String lastSegment = path.substring(path.lastIndexOf('/') + 1);
			return !path.startsWith("api/") && !path.startsWith(".well-known/") && !lastSegment.contains(".");
		}

	}

}
