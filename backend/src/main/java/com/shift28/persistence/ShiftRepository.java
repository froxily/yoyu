package com.shift28.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShiftRepository extends JpaRepository<ShiftEntity, UUID> {

	Optional<ShiftEntity> findByIdAndUserId(UUID id, UUID userId);

	@Query("""
			select s from ShiftEntity s
			where s.userId = :userId and s.startAt < :to and s.endAt > :from
			order by s.startAt""")
	List<ShiftEntity> findOverlapping(UUID userId, Instant from, Instant to);

	boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

}
