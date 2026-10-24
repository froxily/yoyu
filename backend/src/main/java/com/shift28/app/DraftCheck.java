package com.shift28.app;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.shift28.domain.ComplianceReport;
import com.shift28.domain.Shift;
import com.shift28.domain.Status;

public record DraftCheck(ComplianceReport report, List<Shift> overlaps, Map<UUID, String> jobNames) {

	public DraftCheck {
		overlaps = List.copyOf(overlaps);
		jobNames = Map.copyOf(jobNames);
	}

	public Status status() {
		return overlaps.isEmpty() ? report.worstStatus() : Status.VIOLATION;
	}

}
