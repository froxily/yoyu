package com.shift28.web;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shift28.app.AccountService;
import com.shift28.app.CodePurpose;
import com.shift28.app.EmailCodeService;
import com.shift28.app.NotFoundException;
import com.shift28.app.SocialAccountService;
import com.shift28.web.AuthController.CodeRequest;
import com.shift28.web.AuthController.CodeResponse;
import com.shift28.web.AuthController.PasswordRequest;
import com.shift28.web.AuthController.ProfileRequest;
import com.shift28.web.OAuthSession.Intent;
import com.shift28.web.OAuthSession.Kind;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
class AccountController {

	private final AccountService accounts;

	private final SocialAccountService social;

	private final SessionSignIn signIn;

	private final SocialProviders providers;

	private final FindByIndexNameSessionRepository<?> sessions;

	private final Clock clock;

	private final EmailCodeService codes;

	AccountController(AccountService accounts, SocialAccountService social, SessionSignIn signIn,
			SocialProviders providers, FindByIndexNameSessionRepository<?> sessions, Clock clock,
			EmailCodeService codes) {
		this.codes = codes;
		this.accounts = accounts;
		this.social = social;
		this.signIn = signIn;
		this.providers = providers;
		this.sessions = sessions;
		this.clock = clock;
	}

	@PutMapping("/api/account/profile")
	AccountResponse updateProfile(@CurrentUserId UUID userId, @Valid @RequestBody ProfileRequest body,
			HttpServletRequest request) {
		accounts.updateProfile(userId, body.fullName(), body.username());
		return account(userId, request);
	}

	@PostMapping("/api/account/email")
	@ResponseStatus(HttpStatus.ACCEPTED)
	CodeResponse changeEmail(@CurrentUserId UUID userId, @Valid @RequestBody EmailRequest body,
			HttpServletRequest request) {
		signIn.requireRecentProof(request);
		return CodeResponse.of(accounts.startEmailChange(userId, body.email()));
	}

	@PostMapping("/api/account/email/verify")
	AccountResponse verifyEmailChange(@CurrentUserId UUID userId, @Valid @RequestBody CodeRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		accounts.completeEmailChange(userId,
				codes.check(body.verificationId(), CodePurpose.CHANGE_EMAIL, body.code(), userId));
		return keepOnlyThisSession(userId, request, response);
	}

	@PutMapping("/api/account/password")
	AccountResponse setPassword(@CurrentUserId UUID userId, @Valid @RequestBody PasswordRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		signIn.requireRecentProof(request);
		accounts.setPassword(userId, body.password());
		return keepOnlyThisSession(userId, request, response);
	}

	@PostMapping("/api/account/identities/{provider}/link")
	RedirectResponse link(@CurrentUserId UUID userId, @PathVariable String provider, HttpServletRequest request) {
		requireProvider(provider);
		signIn.requireRecentProof(request);
		OAuthSession.saveIntent(request,
				new Intent(Kind.LINK, provider, userId, clock.instant().plus(OAuthSession.LIFETIME)));
		return RedirectResponse.to(provider);
	}

	@PostMapping("/api/account/identities/{provider}/reauthenticate")
	RedirectResponse reauthenticate(@CurrentUserId UUID userId, @PathVariable String provider,
			HttpServletRequest request) {
		requireProvider(provider);
		if (accounts.identities(userId).stream().noneMatch(identity -> identity.getProvider().equals(provider))) {
			throw new NotFoundException("That sign-in method isn't linked.");
		}
		OAuthSession.saveIntent(request,
				new Intent(Kind.REAUTHENTICATE, provider, userId, clock.instant().plus(OAuthSession.LIFETIME)));
		return RedirectResponse.to(provider);
	}

	@DeleteMapping("/api/account/identities/{provider}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unlink(@CurrentUserId UUID userId, @PathVariable String provider, HttpServletRequest request) {
		signIn.requireRecentProof(request);
		social.unlink(userId, provider);
	}

	private AccountResponse keepOnlyThisSession(UUID userId, HttpServletRequest request, HttpServletResponse response) {
		HttpSession current = request.getSession(false);
		sessions.findByPrincipalName(userId.toString())
			.keySet()
			.stream()
			.filter(id -> current == null || !id.equals(current.getId()))
			.forEach(sessions::deleteById);
		signIn.signIn(userId, signIn.recentlyProved(request), request, response);
		return account(userId, request);
	}

	private void requireProvider(String provider) {
		if (!providers.has(provider)) {
			throw new NotFoundException("That sign-in method isn't available.");
		}
	}

	private AccountResponse account(UUID userId, HttpServletRequest request) {
		return AccountResponse.of(accounts.get(userId), accounts.identities(userId), signIn.recentlyProved(request));
	}

	record EmailRequest(@NotBlank @Size(max = 254) String email) {
	}

	record RedirectResponse(String redirect) {

		static RedirectResponse to(String provider) {
			return new RedirectResponse("/oauth2/authorization/" + provider);
		}

	}

}
