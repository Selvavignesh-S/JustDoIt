package com.justdoit.backend.dto;

import java.math.BigDecimal;

import com.justdoit.backend.entity.Category;

// The AI's proposal, already cleaned. Shown for review; nothing is saved (Route plan 3.1).
// Field names match TaskCreateRequest so the app can pass them straight to POST /tasks after review.
public record TaskSuggestion(String title, Category category, BigDecimal estimatedHours) {
}
