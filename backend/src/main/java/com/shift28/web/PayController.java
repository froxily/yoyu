package com.shift28.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shift28.app.PayService;
import com.shift28.domain.JobPay;
import com.shift28.domain.PayMinutes;

import jakarta.validation.Valid;

@RestController
class PayController {

	private final PayService pay;

	PayController(PayService pay) {
		this.pay = pay;
	}

	@GetMapping("/api/pay")
	PayResponse estimate(@CurrentUserId UUID userId, @Valid DateRangeParams range) {
		PayService.Report report = pay.estimate(userId, range.from(), range.to());
		List<JobPayResponse> jobs = report.estimate()
			.jobs()
			.stream()
			.map(job -> JobPayResponse.of(job, report.jobNames().get(job.jobId())))
			.toList();
		return new PayResponse(range.from(), range.to(), jobs, report.estimate().totalYen());
	}

	record PayResponse(LocalDate from, LocalDate to, List<JobPayResponse> jobs, long totalYen) {
	}

	record JobPayResponse(UUID jobId, String name, PayMinutes minutes, long workPayYen, long transportYen,
			long totalYen) {

		static JobPayResponse of(JobPay job, String name) {
			return new JobPayResponse(job.jobId(), name, job.minutes(), job.workPayYen(), job.transportYen(),
					job.totalYen());
		}

	}

}
