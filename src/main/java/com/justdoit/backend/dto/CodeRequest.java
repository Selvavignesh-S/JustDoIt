package com.justdoit.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CodeRequest(@NotBlank(message = "Enter the code from the email.") String code) {
}
