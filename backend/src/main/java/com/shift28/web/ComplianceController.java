package com.shift28.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shift28.app.ComplianceService;
import com.shift28.app.ShiftInput;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/compliance")
class ComplianceController {

	private final ComplianceService compliance;

	ComplianceController(ComplianceService compliance) {
		this.compliance = compliance;
	}

	@GetMapping
	OverviewResponse overview(@CurrentUserId UUID userId, @Valid DateRangeParams range) {
		ComplianceService.Overview overview = compliance.overview(userId, range.from(), range.to());
		return new OverviewResponse(overview.days(),
				overview.issues().stream().map(issue -> IssueResponse.of(issue, overview.jobNames())).toList(),
				overview.capacity());
	}

	@PostMapping("/check")
	ComplianceCheckResponse check(@CurrentUserId UUID userId, @Valid @RequestBody CheckRequest body) {
		ShiftInput draft = new ShiftInput(body.jobId(), body.startAt().toInstant(), body.endAt().toInstant(),
				ShiftController.toInstant(body.breakStart()), ShiftController.toInstant(body.breakEnd()), null);
		return ComplianceCheckResponse
			.of(compliance.check(userId, draft.toShift(body.excludeShiftId()), body.excludeShiftId()));
	}

	record CheckRequest(@NotNull UUID jobId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt,
			OffsetDateTime breakStart, OffsetDateTime breakEnd, UUID excludeShiftId) {
	}

	record OverviewResponse(List<ComplianceService.Day> days, List<IssueResponse> issues,
			List<ComplianceService.Capacity> capacity) {
	}

}
