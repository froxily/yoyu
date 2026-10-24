package com.shift28.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VacationPeriodRepository extends JpaRepository<VacationPeriodEntity, UUID> {

	List<VacationPeriodEntity> findByUserIdOrderByStartDate(UUID userId);

	Optional<VacationPeriodEntity> findByIdAndUserId(UUID id, UUID userId);

}
