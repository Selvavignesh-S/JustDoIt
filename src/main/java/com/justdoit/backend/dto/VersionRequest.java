package com.justdoit.backend.dto;

import jakarta.validation.constraints.NotNull;

// The row version the client last saw (optimistic locking, SRS 19)
public record VersionRequest(@NotNull(message = "The task's version is required.") Integer version) {
}
