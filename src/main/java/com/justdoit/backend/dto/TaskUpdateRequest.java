package com.justdoit.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.justdoit.backend.entity.Category;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH: a null field means "leave unchanged". Reschedule sends only deadline + version (Route plan 3.6).
// An empty description clears it. initialPriority is not here: it can never be edited.
public record TaskUpdateRequest(
		@Size(max = 200, message = "Use at most 200 characters.") @Pattern(regexp = ".*\\S.*", message = "Enter a task name.") String title,
		@Size(max = 2000, message = "Use at most 2000 characters.") String description,
		Category category,
		@DecimalMin(value = "0.1", message = "Use 0.1 to 999.9 hours.") @DecimalMax(value = "999.9", message = "Use 0.1 to 999.9 hours.") @Digits(integer = 4, fraction = 1, message = "Use at most one decimal place.") BigDecimal estimatedHours,
		@Future(message = "Pick a time in the future.") OffsetDateTime deadline,
		@NotNull(message = "The task's version is required.") Integer version) {
}
