package com.shift28.persistence;

import java.time.LocalDate;
import java.util.UUID;

import com.shift28.domain.VacationPeriod;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "vacation_periods")
public class VacationPeriodEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userId;

	private LocalDate startDate;

	private LocalDate endDate;

	private String label;

	@Version
	private long version;

	protected VacationPeriodEntity() {
	}

	public VacationPeriodEntity(UUID userId, LocalDate startDate, LocalDate endDate, String label) {
		this.userId = userId;
		update(startDate, endDate, label);
	}

	public void update(LocalDate startDate, LocalDate endDate, String label) {
		this.startDate = startDate;
		this.endDate = endDate;
		this.label = label;
	}

	public VacationPeriod toDomain() {
		return new VacationPeriod(startDate, endDate);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public long getVersion() {
		return version;
	}

	public String getLabel() {
		return label;
	}

}
