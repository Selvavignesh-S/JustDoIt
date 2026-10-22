package com.justdoit.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailRequest(
		@NotBlank(message = "Enter an email.") @Email(message = "Enter a valid email.") @Size(max = 255, message = "Use at most 255 characters.") String email) {
}
