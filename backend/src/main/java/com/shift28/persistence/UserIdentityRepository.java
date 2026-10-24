package com.shift28.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {

	Optional<UserIdentityEntity> findByProviderAndSubject(String provider, String subject);

	Optional<UserIdentityEntity> findByUserIdAndProvider(UUID userId, String provider);

	List<UserIdentityEntity> findByUserIdOrderByProvider(UUID userId);

}
