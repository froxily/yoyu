package com.shift28.app;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shift28.persistence.JobEntity;
import com.shift28.persistence.JobRepository;
import com.shift28.persistence.ShiftRepository;

@Service
@Transactional
public class JobService {

	private static final String STALE = "This job was changed elsewhere. Reload and try again.";

	private final JobRepository jobs;

	private final ShiftRepository shifts;

	JobService(JobRepository jobs, ShiftRepository shifts) {
		this.jobs = jobs;
		this.shifts = shifts;
	}

	@Transactional(readOnly = true)
	public List<JobEntity> list(UUID userId, boolean includeArchived) {
		return includeArchived ? jobs.findByUserIdOrderByName(userId)
				: jobs.findByUserIdAndArchivedFalseOrderByName(userId);
	}

	public JobEntity create(UUID userId, JobInput input) {
		return jobs.save(new JobEntity(userId, input.name(), input.hourlyWageYen(), input.transportYen(), input.color()));
	}

	public JobEntity update(UUID userId, UUID jobId, JobInput input, long version) {
		JobEntity job = find(userId, jobId);
		if (job.getVersion() != version) {
			throw new StaleEditException(STALE);
		}
		job.update(input.name(), input.hourlyWageYen(), input.transportYen(), input.color());
		return jobs.saveAndFlush(job);
	}

	public void delete(UUID userId, UUID jobId) {
		JobEntity job = find(userId, jobId);
		if (shifts.existsByUserIdAndJobId(userId, jobId)) {
			job.archive();
		}
		else {
			jobs.delete(job);
		}
	}

	public JobEntity restore(UUID userId, UUID jobId) {
		JobEntity job = find(userId, jobId);
		job.restore();
		return jobs.saveAndFlush(job);
	}

	@Transactional(readOnly = true)
	public JobEntity find(UUID userId, UUID jobId) {
		return jobs.findByIdAndUserId(jobId, userId).orElseThrow(() -> new NotFoundException("Job not found."));
	}

}
