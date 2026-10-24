package com.shift28.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_identities")
public class UserIdentityEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userId;

	private String provider;

	private String subject;

	@CreationTimestamp(source = SourceType.DB)
	@Column(updatable = false)
	private Instant createdAt;

	protected UserIdentityEntity() {
	}

	public UserIdentityEntity(UUID userId, String provider, String subject) {
		this.userId = userId;
		this.provider = provider;
		this.subject = subject;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getProvider() {
		return provider;
	}

	public String getSubject() {
		return subject;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
