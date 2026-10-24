package com.shift28;

import org.springframework.boot.SpringApplication;

public class TestShift28Application {

	public static void main(String[] args) {
		SpringApplication.from(Shift28Application::main).with(TestcontainersConfiguration.class).run(args);
	}

}
