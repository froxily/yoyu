package com.shift28.persistence;

import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import com.shift28.domain.RuleSet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "rule_versions")
public class RuleVersionEntity {

	@Id
	private LocalDate effectiveFrom;

	@Column(name = "regular_window_limit_minutes")
	private int regularWindowLimit;

	@Column(name = "vacation_window_limit_minutes")
	private int vacationWindowLimit;

	@Column(name = "vacation_day_limit_minutes")
	private int vacationDayLimit;

	private String sourceUrl;

	private String note;

	protected RuleVersionEntity() {
	}

	public RuleSet toDomain() {
		return new RuleSet(effectiveFrom, regularWindowLimit, vacationWindowLimit, vacationDayLimit);
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getNote() {
		return note;
	}

}
