package com.shift28.web;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shift28.app.AccountService;
import com.shift28.app.AccountUser;
import com.shift28.app.CodePurpose;
import com.shift28.app.EmailCodeService;
import com.shift28.app.EmailCodeService.Issued;
import com.shift28.app.Mailer;
import com.shift28.app.NotFoundException;
import com.shift28.app.RateLimiter;
import com.shift28.app.RateLimiter.Limit;
import com.shift28.app.SocialAccountService;
import com.shift28.persistence.UserEntity;
import com.shift28.web.OAuthSession.PendingSignUp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
class AuthController {

	static final long RESEND_AFTER_SECONDS = 60;

	private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);

	private static final int CODE_REQUESTS = 60;

	private final AccountService accounts;

	private final SocialAccountService social;

	private final SessionSignIn signIn;

	private final RateLimiter limiter;

	private final ClientIpResolver clientIp;

	private final Mailer mailer;

	private final SocialProviders providers;

	private final FindByIndexNameSessionRepository<?> sessions;

	private final Clock clock;

	private final EmailCodeService codes;

	AuthController(AccountService accounts, SocialAccountService social, SessionSignIn signIn, RateLimiter limiter,
			ClientIpResolver clientIp, Mailer mailer, SocialProviders providers,
			FindByIndexNameSessionRepository<?> sessions, Clock clock, EmailCodeService codes) {
		this.codes = codes;
		this.accounts = accounts;
		this.social = social;
		this.signIn = signIn;
		this.limiter = limiter;
		this.clientIp = clientIp;
		this.mailer = mailer;
		this.providers = providers;
		this.sessions = sessions;
		this.clock = clock;
	}

	@GetMapping("/api/auth/providers")
	ProvidersResponse providers() {
		return new ProvidersResponse(providers.has(SocialProviders.GOOGLE), providers.has(SocialProviders.LINE),
				mailer.enabled());
	}

	@PostMapping("/api/auth/register")
	@ResponseStatus(HttpStatus.ACCEPTED)
	CodeResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
		return CodeResponse.of(accounts.startRegistration(body.fullName(), body.username(), body.email(),
				body.password(), clientIp.resolve(request)));
	}

	@PostMapping("/api/auth/register/verify")
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse verifyRegistration(@Valid @RequestBody CodeRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		UserEntity user = accounts.completeRegistration(check(body, CodePurpose.REGISTER, null, request));
		signIn.signIn(user.getId(), true, request, response);
		return account(user.getId(), request);
	}

	@PostMapping("/api/auth/codes/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	CodeResponse resend(@Valid @RequestBody ResendRequest body, HttpServletRequest request) {
		limiter.acquire("code:ip:" + clientIp.resolve(request), CODE_REQUESTS, LOGIN_WINDOW);
		accounts.resend(body.verificationId(), signIn.signedInUser(request));
		return new CodeResponse(body.verificationId(), RESEND_AFTER_SECONDS);
	}

	@PostMapping("/api/auth/login")
	AccountResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		String ip = clientIp.resolve(request);
		String key = accounts.loginKey(body.login());
		String client = RateLimiter.clientKey(ip, key);
		limiter.acquire(new Limit(client, 5, LOGIN_WINDOW), new Limit(key, 20, LOGIN_WINDOW),
				new Limit("login:ip:" + ip, 50, LOGIN_WINDOW));
		AccountUser user;
		try {
			user = signIn.checkPassword(body.login(), body.password());
		}
		catch (AuthenticationException e) {
			throw new BadCredentialsException("Incorrect sign-in details.");
		}
		limiter.release(client);
		limiter.release(key);
		accounts.requireVerifiedEmail(user.id());
		signIn.signIn(user, true, request, response);
		return account(user.id(), request);
	}

	@PostMapping("/api/auth/login/verify")
	AccountResponse verifyLogin(@Valid @RequestBody CodeRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		UserEntity user = accounts.completeExistingVerification(check(body, CodePurpose.VERIFY_EXISTING, null, request));
		signIn.signIn(user.getId(), true, request, response);
		return account(user.getId(), request);
	}

	@PostMapping("/api/auth/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	CodeResponse forgotPassword(@Valid @RequestBody ForgotRequest body, HttpServletRequest request) {
		return CodeResponse.of(accounts.forgotPassword(body.identifier(), clientIp.resolve(request)));
	}

	@PostMapping("/api/auth/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
		UUID userId = accounts.resetPassword(
				check(new CodeRequest(body.verificationId(), body.code()), CodePurpose.RESET_PASSWORD, null, request),
				body.password());
		limiter.reset(RateLimiter.clientKey(clientIp.resolve(request), RateLimiter.accountKey(userId, "login")));
		sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
	}

	@GetMapping("/api/auth/me")
	AccountResponse me(@CurrentUserId UUID userId, HttpServletRequest request) {
		return account(userId, request);
	}

	@PostMapping("/api/auth/reauthenticate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reauthenticate(@CurrentUserId UUID userId, @Valid @RequestBody PasswordRequest body,
			HttpServletRequest request) {
		accounts.checkPassword(userId, body.password());
		signIn.markProved(request);
	}

	@PostMapping("/api/auth/reauthenticate/email")
	@ResponseStatus(HttpStatus.ACCEPTED)
	CodeResponse reauthenticateByEmail(@CurrentUserId UUID userId) {
		return CodeResponse.of(accounts.startEmailReauthentication(userId));
	}

	@PostMapping("/api/auth/reauthenticate/email/verify")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void verifyReauthentication(@CurrentUserId UUID userId, @Valid @RequestBody CodeRequest body,
			HttpServletRequest request) {
		accounts.completeEmailReauthentication(userId, check(body, CodePurpose.REAUTHENTICATE, userId, request));
		signIn.markProved(request);
	}

	@GetMapping("/api/auth/social/pending")
	PendingResponse pendingSignUp(HttpServletRequest request) {
		PendingSignUp pending = requirePending(request);
		return new PendingResponse(pending.provider(), pending.suggestedName(), pending.verifiedEmail(),
				pending.unverifiedEmail());
	}

	@PostMapping("/api/auth/social/complete")
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse completeSocialSignUp(@Valid @RequestBody ProfileRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		PendingSignUp pending = requirePending(request);
		UserEntity user = social.create(pending.provider(), pending.subject(), body.fullName(), body.username(),
				pending.verifiedEmail());
		OAuthSession.clearPending(request);
		signIn.signIn(user.getId(), pending.fresh(), request, response);
		return account(user.getId(), request);
	}

	@DeleteMapping("/api/account")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteAccount(@CurrentUserId UUID userId, Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		signIn.requireRecentProof(request);
		accounts.delete(userId);
		sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	private EmailCodeService.CodeRow check(CodeRequest body, CodePurpose purpose, UUID userId,
			HttpServletRequest request) {
		limiter.acquire("code:ip:" + clientIp.resolve(request), CODE_REQUESTS, LOGIN_WINDOW);
		return codes.check(body.verificationId(), purpose, body.code(), userId);
	}

	private PendingSignUp requirePending(HttpServletRequest request) {
		PendingSignUp pending = OAuthSession.pending(request, clock);
		if (pending == null) {
			throw new NotFoundException("This sign-up has expired. Start again.");
		}
		return pending;
	}

	private AccountResponse account(UUID userId, HttpServletRequest request) {
		return AccountResponse.of(accounts.get(userId), accounts.identities(userId), signIn.recentlyProved(request));
	}

	record ProvidersResponse(boolean google, boolean line, boolean email) {
	}

	record CodeResponse(UUID verificationId, long resendAfterSeconds) {

		static CodeResponse of(Issued issued) {
			return new CodeResponse(issued.id(), RESEND_AFTER_SECONDS);
		}

	}

	record RegisterRequest(@NotBlank @Size(max = 200) String fullName, @NotBlank @Size(max = 50) String username,
			@NotBlank @Size(max = 254) String email, @NotNull @Size(max = 500) String password) {

		@Override
		public String toString() {
			return "RegisterRequest[username=" + username + "]";
		}

	}

	record CodeRequest(@NotNull UUID verificationId, @NotBlank @Size(max = 20) String code) {

		@Override
		public String toString() {
			return "CodeRequest[" + verificationId + "]";
		}

	}

	record ResendRequest(@NotNull UUID verificationId) {
	}

	record LoginRequest(@Size(max = 254) String identifier, @Size(max = 254) String email,
			@NotBlank @Size(max = 500) String password) {

		String login() {
			return identifier != null ? identifier : email;
		}

		@AssertTrue(message = "enter your email or username")
		boolean isIdentified() {
			return login() != null && !login().isBlank();
		}

		@Override
		public String toString() {
			return "LoginRequest";
		}

	}

	record ForgotRequest(@NotBlank @Size(max = 254) String identifier) {
	}

	record ResetRequest(@NotNull UUID verificationId, @NotBlank @Size(max = 20) String code,
			@NotNull @Size(max = 500) String password) {

		@Override
		public String toString() {
			return "ResetRequest[" + verificationId + "]";
		}

	}

	record PasswordRequest(@NotNull @Size(max = 500) String password) {

		@Override
		public String toString() {
			return "PasswordRequest";
		}

	}

	record ProfileRequest(@NotBlank @Size(max = 200) String fullName, @NotBlank @Size(max = 50) String username) {
	}

	record PendingResponse(String provider, String suggestedName, String email, String suggestedEmail) {
	}

}
