package com.justdoit.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Field limits follow the users table and UI decision D20; the 72-byte password cap is checked in AuthService
public record RegisterRequest(
		@NotBlank(message = "Enter your name.") @Size(max = 100, message = "Use at most 100 characters.") String fullName,
		@NotNull(message = "Choose a username.") @Pattern(regexp = "[A-Za-z0-9._]{3,50}", message = "Use 3-50 letters, digits, dots or underscores.") String username,
		@NotNull(message = "Choose a password.") @Size(min = 8, message = "Use at least 8 characters.") String password,
		@Email(message = "Enter a valid email.") @Size(max = 255, message = "Use at most 255 characters.") String email) {
}
