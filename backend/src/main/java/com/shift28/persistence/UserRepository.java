package com.shift28.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	@Query("select u from UserEntity u where lower(u.email) = lower(:email)")
	Optional<UserEntity> findByEmail(String email);

	@Query("select u from UserEntity u where u.username = lower(:username)")
	Optional<UserEntity> findByUsername(String username);

	@Query("select u.sessionVersion from UserEntity u where u.id = :id")
	Optional<Long> findSessionVersion(UUID id);

	@Query(value = "SELECT id FROM users WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
	Optional<UUID> lockById(UUID id);

}
