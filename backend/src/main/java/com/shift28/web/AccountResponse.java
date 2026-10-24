package com.shift28.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.shift28.persistence.UserEntity;
import com.shift28.persistence.UserIdentityEntity;

record AccountResponse(UUID id, String email, String fullName, String username, boolean emailVerified,
		boolean hasPassword, List<Identity> identities, boolean recentlyAuthenticated) {

	static AccountResponse of(UserEntity user, List<UserIdentityEntity> identities, boolean recentlyAuthenticated) {
		return new AccountResponse(user.getId(), user.getEmail(), user.getFullName(), user.getUsername(),
				user.isEmailVerified(), user.hasPassword(),
				identities.stream().map(identity -> new Identity(identity.getProvider(), identity.getCreatedAt())).toList(),
				recentlyAuthenticated);
	}

	record Identity(String provider, Instant linkedAt) {
	}

}
