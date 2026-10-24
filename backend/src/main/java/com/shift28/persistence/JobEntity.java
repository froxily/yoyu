package com.shift28.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import com.shift28.domain.JobRate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "jobs")
public class JobEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userId;

	private String name;

	private int hourlyWageYen;

	private int transportYen;

	private String color;

	private boolean archived;

	@Version
	private long version;

	@CreationTimestamp(source = SourceType.DB)
	@Column(updatable = false)
	private Instant createdAt;

	protected JobEntity() {
	}

	public JobEntity(UUID userId, String name, int hourlyWageYen, int transportYen, String color) {
		this.userId = userId;
		update(name, hourlyWageYen, transportYen, color);
	}

	public void update(String name, int hourlyWageYen, int transportYen, String color) {
		this.name = name;
		this.hourlyWageYen = hourlyWageYen;
		this.transportYen = transportYen;
		this.color = color;
	}

	public void archive() {
		this.archived = true;
	}

	public void restore() {
		this.archived = false;
	}

	public JobRate toRate() {
		return new JobRate(hourlyWageYen, transportYen);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public int getHourlyWageYen() {
		return hourlyWageYen;
	}

	public int getTransportYen() {
		return transportYen;
	}

	public String getColor() {
		return color;
	}

	public long getVersion() {
		return version;
	}

	public boolean isArchived() {
		return archived;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
