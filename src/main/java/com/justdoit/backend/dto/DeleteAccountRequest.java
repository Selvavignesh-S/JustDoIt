package com.justdoit.backend.dto;

import jakarta.validation.constraints.NotBlank;

// Deleting an account asks for the password again, so a phone left unlocked can't wipe it
public record DeleteAccountRequest(@NotBlank(message = "Enter your password.") String password) {
}
