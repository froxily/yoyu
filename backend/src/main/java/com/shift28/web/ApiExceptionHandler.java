package com.shift28.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.shift28.app.ConfirmationRequiredException;
import com.shift28.app.ConflictException;
import com.shift28.app.FieldConflictException;
import com.shift28.app.FieldValidationException;
import com.shift28.app.IdempotencyKeyReusedException;
import com.shift28.app.InvalidCodeException;
import com.shift28.app.NotFoundException;
import com.shift28.app.ReauthenticationRequiredException;
import com.shift28.app.ServiceUnavailableException;
import com.shift28.app.StaleEditException;
import com.shift28.app.TooManyRequestsException;
import com.shift28.app.VerificationRequiredException;
import com.shift28.domain.DomainValidationException;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(DomainValidationException.class)
	ProblemDetail invalid(DomainValidationException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail conflict(ConflictException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		if (e instanceof FieldConflictException field) {
			problem.setProperty("errors", List.of(new FieldProblem(field.field(), field.getMessage())));
		}
		return problem;
	}

	@ExceptionHandler(FieldValidationException.class)
	ProblemDetail fieldInvalid(FieldValidationException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setProperty("errors", List.of(new FieldProblem(e.field(), e.getMessage())));
		return problem;
	}

	@ExceptionHandler(InvalidCodeException.class)
	ProblemDetail invalidCode(InvalidCodeException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setProperty("errors", List.of(new FieldProblem("code", e.getMessage())));
		return problem;
	}

	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<ProblemDetail> tooManyRequests(TooManyRequestsException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
		problem.setProperty("retryAfterSeconds", e.retryAfterSeconds());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
			.body(problem);
	}

	@ExceptionHandler(ServiceUnavailableException.class)
	ProblemDetail unavailable(ServiceUnavailableException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		problem.setProperty("retryable", true);
		return problem;
	}

	@ExceptionHandler(VerificationRequiredException.class)
	ProblemDetail verificationRequired(VerificationRequiredException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
		problem.setProperty("verificationRequired", true);
		problem.setProperty("verificationId", e.verificationId());
		problem.setProperty("resendAfterSeconds", AuthController.RESEND_AFTER_SECONDS);
		return problem;
	}

	@ExceptionHandler(ReauthenticationRequiredException.class)
	ProblemDetail reauthenticationRequired(ReauthenticationRequiredException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
		problem.setProperty("reauthenticate", true);
		return problem;
	}

	@ExceptionHandler(StaleEditException.class)
	ProblemDetail stale(StaleEditException e) {
		return staleProblem(e.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail changedElsewhere() {
		return staleProblem("This was changed elsewhere. Reload and try again.");
	}

	private static ProblemDetail staleProblem(String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
		problem.setProperty("stale", true);
		return problem;
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
	}

	@ExceptionHandler(ConfirmationRequiredException.class)
	ProblemDetail confirmationRequired(ConfirmationRequiredException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
		problem.setProperty("compliance", ComplianceCheckResponse.of(e.check()));
		return problem;
	}

	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail badCredentials() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Incorrect email, username or password.");
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail unauthenticated() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
	}

	@ExceptionHandler(CsrfException.class)
	ProblemDetail csrf() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Missing or invalid CSRF token.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail forbidden() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception e) {
		log.error("Unexpected error", e);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail("The request has invalid fields.");
		problem.setProperty("errors", fieldErrors(ex.getBindingResult().getAllErrors()));
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	private static List<FieldProblem> fieldErrors(List<ObjectError> errors) {
		return errors.stream()
			.map(error -> new FieldProblem(error instanceof FieldError field ? field.getField() : error.getObjectName(),
					error.getDefaultMessage()))
			.toList();
	}

	record FieldProblem(String field, String message) {
	}

}
