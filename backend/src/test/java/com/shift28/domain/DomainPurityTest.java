package com.shift28.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DomainPurityTest {

	private static final Path DOMAIN_SOURCES = Path.of("src/main/java/com/shift28/domain");

	private static final Pattern FORBIDDEN = Pattern.compile(
			"^import (static )?(org\\.springframework|jakarta|javax\\.persistence|org\\.hibernate)\\..*"
					+ "|.*\\b(systemDefault|systemDefaultZone|systemUTC)\\(.*|.*\\.now\\(.*");

	@Test
	void domainUsesNoFrameworkAndNoSystemClockOrZone() throws IOException {
		try (Stream<Path> files = Files.walk(DOMAIN_SOURCES)) {
			List<String> offending = files.filter(file -> file.toString().endsWith(".java"))
				.flatMap(DomainPurityTest::forbiddenLines)
				.toList();

			assertThat(offending).isEmpty();
		}
	}

	private static Stream<String> forbiddenLines(Path file) {
		try {
			return Files.readAllLines(file)
				.stream()
				.filter(line -> FORBIDDEN.matcher(line.strip()).matches())
				.map(line -> file.getFileName() + ": " + line.strip());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
