package com.justdoit.backend.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

// The one error shape every route returns (SRS 24, UI plan 4.3). The app switches on `code`, never on `message`.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fieldErrors) {

	public ApiError(String code, String message) {
		this(code, message, null);
	}

}
