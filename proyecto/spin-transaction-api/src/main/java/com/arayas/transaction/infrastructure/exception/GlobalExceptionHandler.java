package com.arayas.transaction.infrastructure.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
		log.debug("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
		return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(BusinessRuleException.class)
	public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
		log.debug("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
		return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.findFirst()
				.orElse("Validation failed");
		log.debug("{} {}: {}", request.getMethod(), request.getRequestURI(), message);
		return buildResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
	}

	@ExceptionHandler(RestClientException.class)
	public ResponseEntity<ErrorResponse> handleProviderFailure(RestClientException ex, HttpServletRequest request) {
		log.warn("{} {}: provider call failed — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
		return buildResponse(HttpStatus.BAD_GATEWAY, "External provider is unavailable", request.getRequestURI());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
		log.error("{} {}: unexpected error", request.getMethod(), request.getRequestURI(), ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request.getRequestURI());
	}

	private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String path) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
		return ResponseEntity.status(status).body(body);
	}

}
