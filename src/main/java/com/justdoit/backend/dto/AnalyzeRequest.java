package com.justdoit.backend.dto;

import jakarta.validation.constraints.NotBlank;

// Only the description, never the deadline or anything about the user (SRS 8.6 rule 1).
// The 500-character limit is checked after whitespace is collapsed, in TaskService.
public record AnalyzeRequest(@NotBlank(message = "Describe the task.") String description) {
}
