package com.shift28.app;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shift28.persistence.UserEntity;
import com.shift28.persistence.UserIdentityEntity;
import com.shift28.persistence.UserIdentityRepository;
import com.shift28.persistence.UserRepository;

@Service
@Transactional
public class SocialAccountService {

	private final UserRepository users;

	private final UserIdentityRepository identities;

	private final AccountService accounts;

	private final Clock clock;

	SocialAccountService(UserRepository users, UserIdentityRepository identities, AccountService accounts,
			Clock clock) {
		this.users = users;
		this.identities = identities;
		this.accounts = accounts;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<UUID> linkedUser(String provider, String subject) {
		return identities.findByProviderAndSubject(provider, subject).map(UserIdentityEntity::getUserId);
	}

	@Transactional(readOnly = true)
	public boolean emailHasAccount(String email) {
		return email != null && users.findByEmail(email).isPresent();
	}

	public LinkResult link(UUID userId, String provider, String subject) {
		users.lockById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
		Optional<UserIdentityEntity> existing = identities.findByProviderAndSubject(provider, subject);
		if (existing.isPresent()) {
			return existing.get().getUserId().equals(userId) ? LinkResult.LINKED : LinkResult.IDENTITY_IN_USE;
		}
		if (identities.findByUserIdAndProvider(userId, provider).isPresent()) {
			return LinkResult.PROVIDER_ALREADY_LINKED;
		}
		try {
			identities.saveAndFlush(new UserIdentityEntity(userId, provider, subject));
			return LinkResult.LINKED;
		}
		catch (DataIntegrityViolationException e) {
			if (DatabaseErrors.hasSqlState(e, DatabaseErrors.UNIQUE_VIOLATION)) {
				return LinkResult.IDENTITY_IN_USE;
			}
			throw e;
		}
	}

	public UserEntity create(String provider, String subject, String rawFullName, String rawUsername,
			String verifiedEmail) {
		String fullName = AccountRules.fullName(rawFullName);
		String username = AccountRules.username(rawUsername);
		if (identities.findByProviderAndSubject(provider, subject).isPresent()) {
			throw new ConflictException("This account is already registered. Sign in instead.");
		}
		if (users.findByUsername(username).isPresent()) {
			throw new FieldConflictException("username", AccountService.USERNAME_TAKEN);
		}
		String email = verifiedEmail != null && users.findByEmail(verifiedEmail).isEmpty() ? verifiedEmail : null;
		UserEntity user = accounts.saveNew(
				() -> new UserEntity(email, null, fullName, username, email == null ? null : clock.instant()));
		try {
			identities.saveAndFlush(new UserIdentityEntity(user.getId(), provider, subject));
		}
		catch (DataIntegrityViolationException e) {
			if (DatabaseErrors.hasSqlState(e, DatabaseErrors.UNIQUE_VIOLATION)) {
				throw new ConflictException("This account is already registered. Sign in instead.");
			}
			throw e;
		}
		return user;
	}

	public void unlink(UUID userId, String provider) {
		users.lockById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
		UserIdentityEntity identity = identities.findByUserIdAndProvider(userId, provider)
			.orElseThrow(() -> new NotFoundException("That sign-in method isn't linked."));
		UserEntity user = accounts.get(userId);
		long others = identities.findByUserIdOrderByProvider(userId).size() - 1;
		if (!user.hasPassword() && others == 0) {
			throw new ConflictException("Add another way to sign in before removing this one.");
		}
		identities.delete(identity);
	}

	public enum LinkResult {

		LINKED, IDENTITY_IN_USE, PROVIDER_ALREADY_LINKED

	}

}
