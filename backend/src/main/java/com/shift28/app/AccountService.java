package com.shift28.app;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shift28.app.EmailCodeService.CodeRow;
import com.shift28.app.EmailCodeService.Issued;
import com.shift28.app.EmailCodeService.Pending;
import com.shift28.app.EmailCodeService.Resent;
import com.shift28.app.RateLimiter.Limit;
import com.shift28.persistence.UserEntity;
import com.shift28.persistence.UserIdentityEntity;
import com.shift28.persistence.UserIdentityRepository;
import com.shift28.persistence.UserRepository;

@Service
@Transactional
public class AccountService implements UserDetailsService {

	static final String EMAIL_TAKEN = "An account with this email already exists.";

	static final String USERNAME_TAKEN = "That username is taken.";

	static final String EMAIL_CONSTRAINT = "users_email_lower_uq";

	static final String USERNAME_CONSTRAINT = "users_username_uq";

	private static final String MAIL_OFF = "Email sign-in features aren't available yet.";

	private static final Duration HOUR = Duration.ofHours(1);

	private static final Duration QUARTER = Duration.ofMinutes(15);

	private final UserRepository users;

	private final UserIdentityRepository identities;

	private final PasswordEncoder passwordEncoder;

	private final IdempotencyService idempotency;

	private final EmailCodeService codes;

	private final Mailer mailer;

	private final PasswordPolicy passwords;

	private final RateLimiter limiter;

	private final Clock clock;

	AccountService(UserRepository users, UserIdentityRepository identities, PasswordEncoder passwordEncoder,
			IdempotencyService idempotency, EmailCodeService codes, Mailer mailer, PasswordPolicy passwords,
			RateLimiter limiter, Clock clock) {
		this.users = users;
		this.identities = identities;
		this.passwordEncoder = passwordEncoder;
		this.idempotency = idempotency;
		this.codes = codes;
		this.mailer = mailer;
		this.passwords = passwords;
		this.limiter = limiter;
		this.clock = clock;
	}

	public Issued startRegistration(String rawFullName, String rawUsername, String rawEmail, String password,
			String clientIp) {
		mailer.requireEnabled("Email sign-up isn't available yet. Continue with Google or LINE instead.");
		String fullName = AccountRules.fullName(rawFullName);
		String username = AccountRules.username(rawUsername);
		String email = AccountRules.email(rawEmail);
		passwords.check(password, email, username);
		limiter.acquire("register:ip:" + clientIp, 20, HOUR);
		limiter.acquire("register:email:" + email.toLowerCase(Locale.ROOT), 5, HOUR);
		if (users.findByUsername(username).isPresent()) {
			throw new FieldConflictException("username", USERNAME_TAKEN);
		}
		String passwordHash = passwordEncoder.encode(password);
		boolean taken = users.findByEmail(email).isPresent();
		Issued issued = codes.issue(CodePurpose.REGISTER, email, null,
				taken ? null : new Pending(fullName, username, passwordHash), null, !taken);
		if (taken) {
			mailer.sendLater(email, "Sign-up attempt", """
					Someone tried to create an account with this email address, but you already have one.
					Sign in instead, or use "Forgot password?" if you can't remember your password.
					If this wasn't you, you can ignore this email.""");
		}
		else {
			mailCode(email, issued.code(), "Your sign-up code", "Your sign-up code is");
		}
		return issued;
	}

	public UserEntity completeRegistration(CodeRow row) {
		requirePurpose(row, CodePurpose.REGISTER);
		codes.consume(row.id());
		return saveNew(() -> new UserEntity(row.email(), row.passwordHash(), row.fullName(), row.username(),
				clock.instant()));
	}

	public Resent resend(UUID verificationId, UUID currentUserId) {
		mailer.requireEnabled(MAIL_OFF);
		Resent resent = codes.resend(verificationId, currentUserId);
		if (resent.code() != null) {
			String[] text = codeText(resent.row().purpose());
			mailCode(resent.row().email(), resent.code(), text[0], text[1]);
		}
		return resent;
	}

	@Transactional(readOnly = true)
	public String loginKey(String identifier) {
		return limitKey(findByIdentifier(identifier), identifier, "login");
	}

	@Transactional(noRollbackFor = VerificationRequiredException.class)
	public void requireVerifiedEmail(UUID userId) {
		UserEntity user = get(userId);
		if (!mailer.enabled() || user.isEmailVerified()) {
			return;
		}
		Issued issued = codes.issue(CodePurpose.VERIFY_EXISTING, user.getEmail(), userId, null, null, true);
		mailer.sendNow(user.getEmail(), "Confirm your email",
				"Your code to confirm your email is " + issued.code() + ". It expires in 10 minutes.");
		throw new VerificationRequiredException(issued.id());
	}

	public UserEntity completeExistingVerification(CodeRow row) {
		requirePurpose(row, CodePurpose.VERIFY_EXISTING);
		codes.consume(row.id());
		UserEntity user = locked(row.userId());
		if (!row.email().equalsIgnoreCase(user.getEmail())) {
			throw new InvalidCodeException();
		}
		user.markEmailVerified(clock.instant());
		return user;
	}

	public Issued forgotPassword(String identifier, String clientIp) {
		mailer.requireEnabled("Password reset isn't available yet.");
		limiter.acquire("forgot:ip:" + clientIp, 10, HOUR);
		Optional<UserEntity> account = findByIdentifier(identifier);
		String key = limitKey(account, identifier, "forgot");
		limiter.acquire(new Limit(RateLimiter.clientKey(clientIp, key), 3, HOUR), new Limit(key, 10, HOUR));
		boolean resettable = account.filter(UserEntity::hasPassword).isPresent();
		Issued issued = codes.issue(CodePurpose.RESET_PASSWORD, account.map(UserEntity::getEmail).orElse(null),
				account.map(UserEntity::getId).orElse(null), null, null, resettable);
		account.filter(user -> user.getEmail() != null).ifPresent(user -> {
			if (resettable) {
				mailCode(user.getEmail(), issued.code(), "Your password reset code", "Your password reset code is");
			}
			else {
				mailer.sendLater(user.getEmail(), "Password reset", """
						Someone asked to reset the password for your account, but you sign in with Google or LINE,
						so there is no password to reset. Continue with Google or LINE to sign in.
						If this wasn't you, you can ignore this email.""");
			}
		});
		return issued;
	}

	public UUID resetPassword(CodeRow row, String newPassword) {
		requirePurpose(row, CodePurpose.RESET_PASSWORD);
		UserEntity user = locked(row.userId());
		if (!user.hasPassword() || user.getEmail() == null || !user.getEmail().equalsIgnoreCase(row.email())) {
			throw new InvalidCodeException();
		}
		passwords.check(newPassword, user.getEmail(), user.getUsername());
		codes.consume(row.id());
		codes.deleteFor(user.getId(), CodePurpose.RESET_PASSWORD);
		user.changePassword(passwordEncoder.encode(newPassword));
		user.markEmailVerified(clock.instant());
		user.revokeSessions();
		limiter.reset(RateLimiter.accountKey(user.getId(), "login"));
		limiter.reset(RateLimiter.accountKey(user.getId(), "password"));
		mailer.sendLater(user.getEmail(), "Your password was changed",
				"Your password was just changed and you were signed out everywhere. If this wasn't you, reset your password now.");
		return user.getId();
	}

	public Issued startEmailChange(UUID userId, String rawEmail) {
		mailer.requireEnabled("Changing your email isn't available yet.");
		String email = AccountRules.email(rawEmail);
		limiter.acquire(RateLimiter.accountKey(userId, "email-change"), 5, HOUR);
		UserEntity user = get(userId);
		if (email.equalsIgnoreCase(user.getEmail())) {
			throw new FieldValidationException("email", "That's already your email address.");
		}
		boolean taken = users.findByEmail(email).isPresent();
		Issued issued = codes.issue(CodePurpose.CHANGE_EMAIL, email, userId, null, user.getEmail(), !taken);
		if (taken) {
			mailer.sendLater(email, "Email change attempt", """
					Someone tried to use this email address for another account. This address already has an account,
					so nothing was changed. If this wasn't you, you can ignore this email.""");
		}
		else {
			mailCode(email, issued.code(), "Confirm your new email", "Your code to confirm this email address is");
		}
		return issued;
	}

	public UserEntity completeEmailChange(UUID userId, CodeRow row) {
		requirePurpose(row, CodePurpose.CHANGE_EMAIL);
		if (!userId.equals(row.userId())) {
			throw new InvalidCodeException();
		}
		UserEntity user = locked(userId);
		codes.consume(row.id());
		if (!sameEmail(row.previousEmail(), user.getEmail())) {
			throw new ConflictException("Your email changed while this code was pending. Start again.");
		}
		String previous = user.getEmail();
		user.changeEmail(row.email(), clock.instant());
		user.revokeSessions();
		flushUnique(EMAIL_CONSTRAINT, "email", EMAIL_TAKEN);
		if (previous != null) {
			mailer.sendLater(previous, "Your email was changed",
					"The email for your account was changed to another address. If this wasn't you, contact us by replying to this email.");
		}
		return user;
	}

	public Issued startEmailReauthentication(UUID userId) {
		mailer.requireEnabled("Email confirmation isn't available yet.");
		limiter.acquire(RateLimiter.accountKey(userId, "reauth-email"), 5, HOUR);
		UserEntity user = get(userId);
		if (user.getEmail() == null || !user.isEmailVerified()) {
			throw new FieldValidationException("email", "Your account has no confirmed email address.");
		}
		Issued issued = codes.issue(CodePurpose.REAUTHENTICATE, user.getEmail(), userId, null, null, true);
		mailer.sendNow(user.getEmail(), "Your confirmation code",
				"Your confirmation code is " + issued.code() + ". It expires in 10 minutes.");
		return issued;
	}

	public void completeEmailReauthentication(UUID userId, CodeRow row) {
		requirePurpose(row, CodePurpose.REAUTHENTICATE);
		UserEntity user = get(userId);
		if (!userId.equals(row.userId()) || !user.isEmailVerified() || !row.email().equalsIgnoreCase(user.getEmail())) {
			throw new InvalidCodeException();
		}
		codes.consume(row.id());
	}

	public void checkPassword(UUID userId, String password) {
		String key = RateLimiter.accountKey(userId, "password");
		limiter.acquire(key, 5, QUARTER);
		UserEntity user = get(userId);
		if (!user.hasPassword() || password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new FieldValidationException("password", "That password isn't right.");
		}
		limiter.release(key);
	}

	public UserEntity setPassword(UUID userId, String password) {
		UserEntity user = locked(userId);
		boolean changing = user.hasPassword();
		if (user.getEmail() == null || (!changing && !user.isEmailVerified())) {
			throw new FieldValidationException("password", "Add and confirm an email address before setting a password.");
		}
		passwords.check(password, user.getEmail(), user.getUsername());
		user.changePassword(passwordEncoder.encode(password));
		user.revokeSessions();
		if (mailer.enabled()) {
			mailer.sendLater(user.getEmail(), changing ? "Your password was changed" : "A password was added",
					(changing ? "Your password was just changed" : "A password was just added to your account")
							+ " and your other devices were signed out. If this wasn't you, reset your password now.");
		}
		return user;
	}

	public UserEntity updateProfile(UUID userId, String rawFullName, String rawUsername) {
		String fullName = AccountRules.fullName(rawFullName);
		String username = AccountRules.username(rawUsername);
		UserEntity user = locked(userId);
		if (!username.equals(user.getUsername())
				&& users.findByUsername(username).filter(other -> !other.getId().equals(userId)).isPresent()) {
			throw new FieldConflictException("username", USERNAME_TAKEN);
		}
		user.changeProfile(fullName, username);
		flushUnique(USERNAME_CONSTRAINT, "username", USERNAME_TAKEN);
		return user;
	}

	@Transactional(readOnly = true)
	public UserEntity get(UUID userId) {
		return users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
	}

	@Transactional(readOnly = true)
	public List<UserIdentityEntity> identities(UUID userId) {
		return identities.findByUserIdOrderByProvider(userId);
	}

	public void delete(UUID userId) {
		idempotency.deleteAll(userId);
		users.deleteById(userId);
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String identifier) {
		return findByIdentifier(identifier).filter(UserEntity::hasPassword)
			.map(user -> new AccountUser(user.getId(), user.getPasswordHash(), user.getSessionVersion()))
			.orElseThrow(() -> new UsernameNotFoundException("Unknown account."));
	}

	@Transactional(readOnly = true)
	public AccountUser loadById(UUID userId) {
		UserEntity user = get(userId);
		return new AccountUser(user.getId(), null, user.getSessionVersion());
	}

	@Transactional(readOnly = true)
	public Optional<Long> sessionVersion(UUID userId) {
		return users.findSessionVersion(userId);
	}

	UserEntity saveNew(Supplier<UserEntity> user) {
		try {
			return users.saveAndFlush(user.get());
		}
		catch (DataIntegrityViolationException e) {
			if (DatabaseErrors.violates(e, USERNAME_CONSTRAINT)) {
				throw new FieldConflictException("username", USERNAME_TAKEN);
			}
			if (DatabaseErrors.violates(e, EMAIL_CONSTRAINT)) {
				throw new FieldConflictException("email", EMAIL_TAKEN);
			}
			throw e;
		}
	}

	private UserEntity locked(UUID userId) {
		users.lockById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
		return get(userId);
	}

	private static void requirePurpose(CodeRow row, CodePurpose purpose) {
		if (row.purpose() != purpose) {
			throw new InvalidCodeException();
		}
	}

	private void flushUnique(String constraint, String field, String message) {
		try {
			users.flush();
		}
		catch (DataIntegrityViolationException e) {
			if (DatabaseErrors.violates(e, constraint)) {
				throw new FieldConflictException(field, message);
			}
			throw e;
		}
	}

	private Optional<UserEntity> findByIdentifier(String identifier) {
		if (identifier == null || identifier.isBlank()) {
			return Optional.empty();
		}
		String trimmed = identifier.strip();
		return AccountRules.looksLikeEmail(trimmed) ? users.findByEmail(trimmed) : users.findByUsername(trimmed);
	}

	private static String limitKey(Optional<UserEntity> account, String identifier, String scope) {
		return account.map(user -> RateLimiter.accountKey(user.getId(), scope))
			.orElseGet(() -> "identifier:" + normalizedIdentifier(identifier) + ":" + scope);
	}

	private static String normalizedIdentifier(String identifier) {
		return identifier == null ? "" : identifier.strip().toLowerCase(Locale.ROOT);
	}

	private static boolean sameEmail(String a, String b) {
		return a == null ? b == null : a.equalsIgnoreCase(b);
	}

	private void mailCode(String to, String code, String subject, String lead) {
		mailer.sendLater(to, subject, lead + " " + code + """
				. It expires in 10 minutes.
				If you didn't ask for this, you can ignore this email.""");
	}

	private static String[] codeText(CodePurpose purpose) {
		return switch (purpose) {
			case REGISTER -> new String[] { "Your sign-up code", "Your sign-up code is" };
			case VERIFY_EXISTING -> new String[] { "Confirm your email", "Your code to confirm your email is" };
			case RESET_PASSWORD -> new String[] { "Your password reset code", "Your password reset code is" };
			case CHANGE_EMAIL -> new String[] { "Confirm your new email", "Your code to confirm this email address is" };
			case REAUTHENTICATE -> new String[] { "Your confirmation code", "Your confirmation code is" };
		};
	}

}
