package com.shift28.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.shift28.app.IdempotencyService;
import com.shift28.app.IdempotencyService.StoredResponse;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
class IdempotentWrites {

	static final String HEADER = "Idempotency-Key";

	static final String REPLAYED = "Idempotent-Replayed";

	private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{1,255}");

	private final IdempotencyService idempotency;

	private final TransactionTemplate transaction;

	private final JsonMapper json;

	private final JsonMapper canonical;

	IdempotentWrites(IdempotencyService idempotency, TransactionTemplate transaction, JsonMapper json) {
		this.idempotency = idempotency;
		this.transaction = transaction;
		this.json = json;
		this.canonical = json.rebuild()
			.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.build();
	}

	ResponseEntity<?> created(UUID userId, String key, HttpServletRequest request, Object body, Supplier<?> write) {
		if (key == null) {
			return ResponseEntity.status(HttpStatus.CREATED).body(write.get());
		}
		if (!VALID_KEY.matcher(key).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Idempotency-Key must be 1 to 255 visible ASCII characters.");
		}
		byte[] fingerprint = fingerprint(request.getMethod(), request.getRequestURI(), body);
		return transaction.execute(status -> {
			Optional<StoredResponse> stored = idempotency.claim(userId, key, fingerprint);
			if (stored.isPresent()) {
				return ResponseEntity.status(stored.get().status())
					.contentType(MediaType.APPLICATION_JSON)
					.header(REPLAYED, "true")
					.body(stored.get().body());
			}
			String response = json.writeValueAsString(write.get());
			idempotency.complete(userId, key, HttpStatus.CREATED.value(), response);
			return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(response);
		});
	}

	private byte[] fingerprint(String method, String path, Object body) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update((method + " " + path + "\n").getBytes(StandardCharsets.UTF_8));
			return digest.digest(canonical.writeValueAsBytes(body));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
