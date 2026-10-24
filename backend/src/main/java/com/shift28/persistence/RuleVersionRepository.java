package com.shift28.persistence;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleVersionRepository extends JpaRepository<RuleVersionEntity, LocalDate> {

}
