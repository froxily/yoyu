package com.shift28.web;

import java.util.List;
import java.util.stream.Stream;

import com.shift28.app.DraftCheck;
import com.shift28.domain.Status;

record ComplianceCheckResponse(Status status, List<IssueResponse> issues) {

	static ComplianceCheckResponse of(DraftCheck check) {
		return new ComplianceCheckResponse(check.status(),
				Stream.concat(check.overlaps().stream().map(IssueResponse::overlap),
						check.report().issues().stream().map(issue -> IssueResponse.of(issue, check.jobNames())))
					.toList());
	}

}
