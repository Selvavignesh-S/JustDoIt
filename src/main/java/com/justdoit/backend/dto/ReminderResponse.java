package com.justdoit.backend.dto;

// "reason" is ready-to-show text, e.g. "Due in 5 hours" (UI plan 4.2)
public record ReminderResponse(long taskId, String title, String reason) {
}
