package com.shift28.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@DynamicUpdate
@Table(name = "users")
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String email;

	private String passwordHash;

	private String fullName;

	private String username;

	private Instant emailVerifiedAt;

	private long sessionVersion;

	@CreationTimestamp(source = SourceType.DB)
	@Column(updatable = false)
	private Instant createdAt;

	protected UserEntity() {
	}

	public UserEntity(String email, String passwordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public UserEntity(String email, String passwordHash, String fullName, String username, Instant emailVerifiedAt) {
		this(email, passwordHash);
		this.fullName = fullName;
		this.username = username;
		this.emailVerifiedAt = emailVerifiedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public String getUsername() {
		return username;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public long getSessionVersion() {
		return sessionVersion;
	}

	public void revokeSessions() {
		sessionVersion++;
	}

	public boolean hasPassword() {
		return passwordHash != null;
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	public void changeProfile(String fullName, String username) {
		this.fullName = fullName;
		this.username = username;
	}

	public void changeEmail(String email, Instant verifiedAt) {
		this.email = email;
		this.emailVerifiedAt = verifiedAt;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public void markEmailVerified(Instant verifiedAt) {
		this.emailVerifiedAt = verifiedAt;
	}

}
