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

	// Same answer for "doesn't exist" and "belongs to someone else", so ids can't be probed (SRS 7.4)
	public static ApiException notAvailable() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_AVAILABLE", "This task isn't available.", null);
	}

	// The client's row version is stale: someone (usually the same user on another device) changed it first (SRS 19)
	public static ApiException conflict() {
		return new ApiException(HttpStatus.CONFLICT, "CONFLICT", "This task changed on another device. Reload to see the latest version.", null);
	}

	public static ApiException fileConflict() {
		return new ApiException(HttpStatus.CONFLICT, "CONFLICT", "This file changed on another device.", null);
	}

	public static ApiException taskCompleted() {
		return new ApiException(HttpStatus.BAD_REQUEST, "TASK_COMPLETED", "Completed tasks can't be edited. Mark it as pending first.", null);
	}

	// Every AI provider failed: the app must open the manual form. Identified by code, since a sleeping host also gives 5xx.
	public static ApiException aiUnavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", "The assistant isn't available right now. Fill in the details to create your task.", null);
	}

	// MAIL_PROVIDER=none: same answer for every email address, so it reveals nothing about accounts
	public static ApiException emailDisabled() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DISABLED",
				"Emails can't be sent yet. Your email is saved and will be used for password reset once email is turned on.", null);
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
