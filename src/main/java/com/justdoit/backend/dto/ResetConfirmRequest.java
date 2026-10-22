package com.justdoit.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResetConfirmRequest(
		@NotBlank(message = "Enter the code from the email.") String code,
		@NotNull(message = "Choose a password.") @Size(min = 8, message = "Use at least 8 characters.") String newPassword) {
}
