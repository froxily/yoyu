package com.shift28.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SourceType;
import org.hibernate.generator.EventType;

import com.shift28.domain.Shift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "shifts")
public class ShiftEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userId;

	private UUID jobId;

	private Instant startAt;

	private Instant endAt;

	private Instant breakStart;

	private Instant breakEnd;

	private String note;

	@Version
	private long version;

	@CreationTimestamp(source = SourceType.DB)
	@Column(updatable = false)
	private Instant createdAt;

	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(insertable = false, updatable = false)
	private Instant updatedAt;

	protected ShiftEntity() {
	}

	public ShiftEntity(UUID userId, UUID jobId, Instant startAt, Instant endAt, Instant breakStart, Instant breakEnd,
			String note) {
		this.userId = userId;
		update(jobId, startAt, endAt, breakStart, breakEnd, note);
	}

	public void update(UUID jobId, Instant startAt, Instant endAt, Instant breakStart, Instant breakEnd, String note) {
		this.jobId = jobId;
		this.startAt = startAt;
		this.endAt = endAt;
		this.breakStart = breakStart;
		this.breakEnd = breakEnd;
		this.note = note;
	}

	public Shift toDomain() {
		return new Shift(id, jobId, startAt, endAt, breakStart, breakEnd);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getJobId() {
		return jobId;
	}

	public Instant getStartAt() {
		return startAt;
	}

	public Instant getEndAt() {
		return endAt;
	}

	public Instant getBreakStart() {
		return breakStart;
	}

	public Instant getBreakEnd() {
		return breakEnd;
	}

	public long getVersion() {
		return version;
	}

	public String getNote() {
		return note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
