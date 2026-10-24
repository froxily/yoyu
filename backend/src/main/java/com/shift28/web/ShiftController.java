package com.shift28.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shift28.app.ShiftInput;
import com.shift28.app.ShiftService;
import com.shift28.domain.Rules;
import com.shift28.persistence.ShiftEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/shifts")
class ShiftController {

	private final ShiftService shifts;

	private final IdempotentWrites idempotent;

	ShiftController(ShiftService shifts, IdempotentWrites idempotent) {
		this.shifts = shifts;
		this.idempotent = idempotent;
	}

	@GetMapping
	List<ShiftResponse> list(@CurrentUserId UUID userId, @Valid DateRangeParams range) {
		return shifts.list(userId, range.from(), range.to()).stream().map(ShiftResponse::of).toList();
	}

	@PostMapping
	ResponseEntity<?> create(@CurrentUserId UUID userId, @Valid @RequestBody ShiftRequest body,
			@RequestHeader(name = IdempotentWrites.HEADER, required = false) String key, HttpServletRequest request) {
		return idempotent.created(userId, key, request, body, () -> SavedShiftResponse
			.of(shifts.create(userId, body.toInput(), Boolean.TRUE.equals(body.confirmed()))));
	}

	@PutMapping("/{id}")
	SavedShiftResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Validated({ Default.class, Update.class }) @RequestBody ShiftRequest body) {
		return SavedShiftResponse
			.of(shifts.update(userId, id, body.toInput(), body.version(), Boolean.TRUE.equals(body.confirmed())));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		shifts.delete(userId, id);
	}

	static OffsetDateTime inZone(Instant instant) {
		return instant == null ? null : instant.atZone(Rules.ZONE).toOffsetDateTime();
	}

	static Instant toInstant(OffsetDateTime time) {
		return time == null ? null : time.toInstant();
	}

	record ShiftRequest(@NotNull UUID jobId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt,
			OffsetDateTime breakStart, OffsetDateTime breakEnd, @Size(max = 500) String note, Boolean confirmed,
			@NotNull(groups = Update.class) Long version) {

		ShiftInput toInput() {
			return new ShiftInput(jobId, startAt.toInstant(), endAt.toInstant(), toInstant(breakStart),
					toInstant(breakEnd), note);
		}

	}

	record ShiftResponse(UUID id, UUID jobId, OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime breakStart,
			OffsetDateTime breakEnd, String note, int workedMinutes, long version) {

		static ShiftResponse of(ShiftEntity shift) {
			return new ShiftResponse(shift.getId(), shift.getJobId(), inZone(shift.getStartAt()),
					inZone(shift.getEndAt()), inZone(shift.getBreakStart()), inZone(shift.getBreakEnd()),
					shift.getNote(), shift.toDomain().workedMinutes(), shift.getVersion());
		}

	}

	record SavedShiftResponse(ShiftResponse shift, ComplianceCheckResponse compliance) {

		static SavedShiftResponse of(ShiftService.Saved saved) {
			return new SavedShiftResponse(ShiftResponse.of(saved.shift()), ComplianceCheckResponse.of(saved.check()));
		}

	}

}
