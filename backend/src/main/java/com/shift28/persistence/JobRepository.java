package com.shift28.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {

	List<JobEntity> findByUserIdOrderByName(UUID userId);

	List<JobEntity> findByUserIdAndArchivedFalseOrderByName(UUID userId);

	Optional<JobEntity> findByIdAndUserId(UUID id, UUID userId);

}
