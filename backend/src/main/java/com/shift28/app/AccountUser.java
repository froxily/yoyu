package com.shift28.app;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AccountUser implements UserDetails, CredentialsContainer {

	private final UUID id;

	private final long sessionVersion;

	private String passwordHash;

	AccountUser(UUID id, String passwordHash, long sessionVersion) {
		this.id = id;
		this.passwordHash = passwordHash;
		this.sessionVersion = sessionVersion;
	}

	public UUID id() {
		return id;
	}

	public long sessionVersion() {
		return sessionVersion;
	}

	@Override
	public String getUsername() {
		return id.toString();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}

	@Override
	public String toString() {
		return "AccountUser[" + id + "]";
	}

}
