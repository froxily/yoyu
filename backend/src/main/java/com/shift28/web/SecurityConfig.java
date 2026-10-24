package com.shift28.web;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.shift28.app.AccountService;
import com.shift28.app.RateLimiter;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data:; "
			+ "style-src 'self' 'unsafe-inline'; style-src-elem 'self'; object-src 'none'; "
			+ "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository, CookieSerializer cookieSerializer,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
			SocialProviders providers, OAuthHandlers oauthHandlers, AccountService accounts, Clock clock,
			RateLimiter limiter, ClientIpResolver clientIp, SessionCleanup sessionCleanup) {
		http.authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/providers",
				"/api/auth/social/pending")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/register/verify", "/api/auth/login",
					"/api/auth/login/verify", "/api/auth/codes/resend", "/api/auth/password/forgot",
					"/api/auth/password/reset", "/api/auth/social/complete")
			.permitAll()
			.requestMatchers("/api/**")
			.authenticated()
			.anyRequest()
			.permitAll())
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
			.addFilterBefore(new SessionVersionFilter(accounts), AuthorizationFilter.class)
			.addFilterAfter(new SessionCookieRefreshFilter(cookieSerializer), AuthorizationFilter.class)
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.requestCache(RequestCacheConfigurer::disable)
			.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
				.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
						"camera=(), microphone=(), geolocation=(), payment=()")))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(
						(request, response, e) -> exceptionResolver.resolveException(request, response, null, e))
				.accessDeniedHandler(
						(request, response, e) -> exceptionResolver.resolveException(request, response, null, e)))
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable);
		if (providers.enabled()) {
			ClientRegistrationRepository registrations = providers.repository();
			DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
					registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
			resolver.setAuthorizationRequestCustomizer(new OAuthRequestCustomizer(clock));
			http.addFilterBefore(new OAuthStartLimitFilter(limiter, clientIp, sessionCleanup),
					OAuth2AuthorizationRequestRedirectFilter.class);
			http.oauth2Login(oauth -> oauth.loginPage("/login")
				.clientRegistrationRepository(registrations)
				.authorizedClientRepository(new NoAuthorizedClients())
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver)
					.authorizationRequestRepository(new CallbackAuthorizationRequests()))
				.withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {

					@Override
					public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
						filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
						return filter;
					}

				})
				.successHandler(oauthHandlers)
				.failureHandler(oauthHandlers));
		}
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

}
