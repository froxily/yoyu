package com.shift28.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shift28.domain.Assessment;
import com.shift28.domain.Attribution;
import com.shift28.domain.Check;
import com.shift28.domain.Contribution;
import com.shift28.domain.Shift;
import com.shift28.domain.Status;

@JsonInclude(JsonInclude.Include.NON_NULL)
record IssueResponse(String type, LocalDate start, LocalDate end, Long minutes, Integer limitMinutes, Status status,
		Attribution attribution, UUID shiftId, Boolean rulesChanged, List<LocalDate> ruleVersions,
		List<ContributionResponse> contributions) {

	static final String OVERLAP = "OVERLAP";

	static IssueResponse of(Check check, Map<UUID, String> jobNames) {
		Assessment reported = check.reported();
		boolean changed = check.rulesChanged();
		List<ContributionResponse> contributions = check.contributions()
			.stream()
			.map(contribution -> ContributionResponse.of(contribution, jobNames))
			.toList();
		return new IssueResponse(check.type().name(), check.start(), check.end(), reported.minutes(),
				reported.limitMinutes(), reported.status(), reported.attribution(), null, changed ? true : null,
				changed ? check.ruleVersions() : null, contributions.isEmpty() ? null : contributions);
	}

	static IssueResponse overlap(Shift shift) {
		return new IssueResponse(OVERLAP, shift.startDate(), shift.lastDate(), null, null, Status.VIOLATION, null,
				shift.id(), null, null, null);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ContributionResponse(UUID shiftId, UUID jobId, String jobName, OffsetDateTime startAt, int minutes,
			boolean draft) {

		static ContributionResponse of(Contribution contribution, Map<UUID, String> jobNames) {
			return new ContributionResponse(contribution.shiftId(), contribution.jobId(),
					jobNames.get(contribution.jobId()), ShiftController.inZone(contribution.startAt()),
					contribution.minutes(), contribution.draft());
		}

	}

}
