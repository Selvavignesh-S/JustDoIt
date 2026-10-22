package com.justdoit.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;

// The deterministic priority rule engine (SRS 11-12). The AI never decides priority.
// First draft, chosen 2026-10-22; every threshold is here so it can be tuned from user feedback in one place.
public final class PriorityEngine {

	public static final Duration DUE_TOMORROW_WINDOW = Duration.ofHours(24);
	public static final Duration DUE_SOON_WINDOW = Duration.ofHours(72);

	// ratio = hours until deadline / estimated hours of work
	private static final double HIGH_BELOW_RATIO = 3;
	private static final double MEDIUM_BELOW_RATIO = 8;

	// These usually weigh more on grades, so they rank one level higher (never above HIGH)
	private static final Set<Category> HIGH_STAKES = EnumSet.of(Category.EXAM, Category.PROJECT, Category.PRESENTATION);

	private PriorityEngine() {
	}

	// One rule for everything: creation (initial = current), reschedule, reopen and the scheduler.
	// A task created under 24 h before its deadline starts at DUE_TOMORROW (changed from SRS 11 on 2026-10-22).
	// Overdue tasks stay at DUE_TOMORROW.
	public static Priority current(Category category, BigDecimal estimatedHours, Instant deadline, Instant now) {
		Duration left = Duration.between(now, deadline);
		if (left.compareTo(DUE_TOMORROW_WINDOW) < 0) {
			return Priority.DUE_TOMORROW;
		}
		double ratio = left.toSeconds() / 3600.0 / estimatedHours.doubleValue();
		Priority base = ratio < HIGH_BELOW_RATIO ? Priority.HIGH : ratio < MEDIUM_BELOW_RATIO ? Priority.MEDIUM : Priority.LOW;
		if (HIGH_STAKES.contains(category)) {
			return base == Priority.LOW ? Priority.MEDIUM : Priority.HIGH;
		}
		return base;
	}

	public static Priority current(Task task, Instant now) {
		return current(task.getCategory(), task.getEstimatedHours(), task.getDeadline(), now);
	}

	// Dashboard states (SRS 9.1): derived from the server clock, never stored
	public static boolean isOverdue(Task task, Instant now) {
		return task.getStatus() == TaskStatus.PENDING && task.getDeadline().isBefore(now);
	}

	public static boolean isDueSoon(Task task, Instant now) {
		return task.getStatus() == TaskStatus.PENDING && !task.getDeadline().isBefore(now)
				&& task.getDeadline().isBefore(now.plus(DUE_SOON_WINDOW));
	}

}
