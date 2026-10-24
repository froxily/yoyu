package com.shift28.web;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class CallbackAuthorizationRequests implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	private static final String COMPLETED = CallbackAuthorizationRequests.class.getName() + ".completed";

	private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> sessions = new HttpSessionOAuth2AuthorizationRequestRepository();

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return sessions.loadAuthorizationRequest(request);
	}

	@Override
	public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
			HttpServletResponse response) {
		sessions.saveAuthorizationRequest(authorizationRequest, request, response);
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
			HttpServletResponse response) {
		OAuth2AuthorizationRequest removed = sessions.removeAuthorizationRequest(request, response);
		if (removed != null) {
			request.setAttribute(COMPLETED, removed);
		}
		return removed;
	}

	static boolean forcedLogin(HttpServletRequest request) {
		return request.getAttribute(COMPLETED) instanceof OAuth2AuthorizationRequest completed
				&& "login".equals(completed.getAdditionalParameters().get("prompt"));
	}

}
