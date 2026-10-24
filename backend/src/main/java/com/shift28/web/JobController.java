package com.shift28.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shift28.app.JobInput;
import com.shift28.app.JobService;
import com.shift28.persistence.JobEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

@RestController
@RequestMapping("/api/jobs")
class JobController {

	private final JobService jobs;

	private final IdempotentWrites idempotent;

	JobController(JobService jobs, IdempotentWrites idempotent) {
		this.jobs = jobs;
		this.idempotent = idempotent;
	}

	@GetMapping
	List<JobResponse> list(@CurrentUserId UUID userId, @RequestParam(defaultValue = "false") boolean includeArchived) {
		return jobs.list(userId, includeArchived).stream().map(JobResponse::of).toList();
	}

	@PostMapping
	ResponseEntity<?> create(@CurrentUserId UUID userId, @Valid @RequestBody JobRequest body,
			@RequestHeader(name = IdempotentWrites.HEADER, required = false) String key, HttpServletRequest request) {
		return idempotent.created(userId, key, request, body, () -> JobResponse.of(jobs.create(userId, body.toInput())));
	}

	@PutMapping("/{id}")
	JobResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Validated({ Default.class, Update.class }) @RequestBody JobRequest body) {
		return JobResponse.of(jobs.update(userId, id, body.toInput(), body.version()));
	}

	@PostMapping("/{id}/restore")
	JobResponse restore(@CurrentUserId UUID userId, @PathVariable UUID id) {
		return JobResponse.of(jobs.restore(userId, id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		jobs.delete(userId, id);
	}

	record JobRequest(@NotBlank @Size(max = 100) String name, @NotNull @Positive Integer hourlyWageYen,
			@PositiveOrZero Integer transportYen, @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
			@NotNull(groups = Update.class) Long version) {

		JobInput toInput() {
			return JobInput.withDefaults(name, hourlyWageYen, transportYen, color);
		}

	}

	record JobResponse(UUID id, String name, int hourlyWageYen, int transportYen, String color, boolean archived,
			long version) {

		static JobResponse of(JobEntity job) {
			return new JobResponse(job.getId(), job.getName(), job.getHourlyWageYen(), job.getTransportYen(),
					job.getColor(), job.isArchived(), job.getVersion());
		}

	}

}
