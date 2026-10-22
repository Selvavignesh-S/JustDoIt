package com.justdoit.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.justdoit.backend.entity.Category;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// No priority field on purpose: the server computes it (Route plan 3.2). Unknown JSON fields are ignored.
// Hours: @DecimalMin/@DecimalMax own the range; @Digits only limits decimals (its integer=4 never triggers first).
public record TaskCreateRequest(
		@NotBlank(message = "Enter a task name.") @Size(max = 200, message = "Use at most 200 characters.") String title,
		@Size(max = 2000, message = "Use at most 2000 characters.") String description,
		@NotNull(message = "Choose a category.") Category category,
		@NotNull(message = "Enter the hours.") @DecimalMin(value = "0.1", message = "Use 0.1 to 999.9 hours.") @DecimalMax(value = "999.9", message = "Use 0.1 to 999.9 hours.") @Digits(integer = 4, fraction = 1, message = "Use at most one decimal place.") BigDecimal estimatedHours,
		@NotNull(message = "Pick a deadline.") @Future(message = "Pick a time in the future.") OffsetDateTime deadline) {
}
