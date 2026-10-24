package com.shift28.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FailingController {

	@GetMapping("/api/test/failure")
	void fail() {
		throw new IllegalStateException("secret connection string jdbc:postgresql://db/prod");
	}

}
