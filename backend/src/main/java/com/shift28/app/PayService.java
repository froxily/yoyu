package com.shift28.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shift28.domain.PayCalculator;
import com.shift28.domain.PayEstimate;
import com.shift28.persistence.JobEntity;
import com.shift28.persistence.JobRepository;
import com.shift28.persistence.ShiftEntity;
import com.shift28.persistence.ShiftRepository;

@Service
@Transactional(readOnly = true)
public class PayService {

	private final JobRepository jobs;

	private final ShiftRepository shifts;

	PayService(JobRepository jobs, ShiftRepository shifts) {
		this.jobs = jobs;
		this.shifts = shifts;
	}

	public Report estimate(UUID userId, LocalDate from, LocalDate to) {
		List<JobEntity> userJobs = jobs.findByUserIdOrderByName(userId);
		PayEstimate estimate = PayCalculator.estimate(
				shifts.findOverlapping(userId, ComplianceService.startOf(from), ComplianceService.startOf(to.plusDays(1)))
					.stream()
					.map(ShiftEntity::toDomain)
					.toList(),
				userJobs.stream().collect(Collectors.toMap(JobEntity::getId, JobEntity::toRate)), from, to);
		return new Report(estimate, userJobs.stream().collect(Collectors.toMap(JobEntity::getId, JobEntity::getName)));
	}

	public record Report(PayEstimate estimate, Map<UUID, String> jobNames) {
	}

}
