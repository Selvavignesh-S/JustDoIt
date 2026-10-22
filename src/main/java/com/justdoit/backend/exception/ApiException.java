package com.justdoit.backend.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

// Thrown from services; GlobalExceptionHandler turns it into an ApiError response
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final Map<String, String> fieldErrors;

	public ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
		super(message);
		this.status = status;
		this.code = code;
		this.fieldErrors = fieldErrors;
	}

	public static ApiException validation(Map<String, String> fieldErrors) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Some fields need attention.", fieldErrors);
	}

	public static ApiException validation(String field, String message) {
		return validation(Map.of(field, message));
	}

	// Same message for unknown username and wrong password, so usernames can't be probed (Route plan 2.2)
	public static ApiException badCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Username or password is incorrect.", null);
	}

	public static ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in again to continue.", null);
	}

	public static ApiException invalidToken() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "This link has expired or was already used.", null);
	}

	public static ApiException rateLimited() {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests. Try again in a few minutes.", null);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}

}
