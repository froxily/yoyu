package com.shift28.web;

import java.time.LocalDate;
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

import com.shift28.app.VacationService;
import com.shift28.persistence.VacationPeriodEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

@RestController
@RequestMapping("/api/vacations")
class VacationController {

	private final VacationService vacations;

	private final IdempotentWrites idempotent;

	VacationController(VacationService vacations, IdempotentWrites idempotent) {
		this.vacations = vacations;
		this.idempotent = idempotent;
	}

	@GetMapping
	List<VacationResponse> list(@CurrentUserId UUID userId) {
		return vacations.list(userId).stream().map(VacationResponse::of).toList();
	}

	@PostMapping
	ResponseEntity<?> create(@CurrentUserId UUID userId, @Valid @RequestBody VacationRequest body,
			@RequestHeader(name = IdempotentWrites.HEADER, required = false) String key, HttpServletRequest request) {
		return idempotent.created(userId, key, request, body,
				() -> VacationResponse.of(vacations.create(userId, body.startDate(), body.endDate(), body.label())));
	}

	@PutMapping("/{id}")
	VacationResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Validated({ Default.class, Update.class }) @RequestBody VacationRequest body) {
		return VacationResponse
			.of(vacations.update(userId, id, body.startDate(), body.endDate(), body.label(), body.version()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		vacations.delete(userId, id);
	}

	record VacationRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @Size(max = 100) String label,
			@NotNull(groups = Update.class) Long version) {
	}

	record VacationResponse(UUID id, LocalDate startDate, LocalDate endDate, String label, long version) {

		static VacationResponse of(VacationPeriodEntity vacation) {
			return new VacationResponse(vacation.getId(), vacation.getStartDate(), vacation.getEndDate(),
					vacation.getLabel(), vacation.getVersion());
		}

	}

}
