package com.justdoit.backend.dto;

import java.util.Map;

import com.justdoit.backend.entity.Priority;

// Dashboard ladder and chip counts over ALL pending tasks; filters never change them (Route plan 3.4)
public record TaskSummaryResponse(Map<Priority, Long> pending, long dueSoon, long overdue) {
}
