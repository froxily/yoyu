package com.shift28.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class HealthController {

	@GetMapping("/health")
	Health health() {
		return new Health("UP");
	}

	record Health(String status) {
	}

}
