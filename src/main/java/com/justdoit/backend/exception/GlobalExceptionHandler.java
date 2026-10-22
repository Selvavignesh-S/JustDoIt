package com.justdoit.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApi(ApiException ex) {
		return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
	}

	// @Valid failures on request bodies: one message per field, so the app can show it under that input
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
		return handleApi(ApiException.validation(fields));
	}

	// A bad query parameter, e.g. ?priority=URGENT
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return handleApi(ApiException.validation(ex.getName(), "This value isn't allowed."));
	}

	// Two saves raced past the version check; @Version made the second one fail
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		return handleApi(ApiException.conflict());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest().body(new ApiError("VALIDATION", "The request body is missing or malformed."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleOther(Exception ex) {
		// Spring's own MVC errors (unknown path, wrong method, ...) carry their status; keep it instead of turning them into 500s
		if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
			HttpStatusCode status = er.getStatusCode();
			String code = status.value() == 404 ? "NOT_AVAILABLE" : "VALIDATION";
			return ResponseEntity.status(status).body(new ApiError(code, "This request isn't supported."));
		}
		log.error("Unhandled error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiError("SERVER", "Something went wrong on our side. Try again."));
	}

}
