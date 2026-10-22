package com.justdoit.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;

// The reminder policy (SRS 13), chosen 2026-10-22. Pure rules over a task and "now": no database, no AI.
// Uses currentPriority, never initialPriority, so reminders follow present urgency.
public final class ReminderPolicy {

	private ReminderPolicy() {
	}

	// Overdue, DUE_TOMORROW, HIGH, and MEDIUM only when due within the 72 h "due soon" window
	public static boolean qualifies(Task task, Instant now) {
		if (task.getStatus() != TaskStatus.PENDING) {
			return false;
		}
		return switch (task.getCurrentPriority()) {
			case DUE_TOMORROW, HIGH -> true;
			case MEDIUM -> PriorityEngine.isDueSoon(task, now) || PriorityEngine.isOverdue(task, now);
			// Overdue always qualifies, even if the scheduler hasn't moved its stored priority yet
			case LOW -> PriorityEngine.isOverdue(task, now);
		};
	}

	// Overdue first, then by urgency, then soonest deadline; id keeps ties stable
	public static Comparator<Task> order(Instant now) {
		return Comparator.<Task>comparingInt(t -> rank(t, now))
				.thenComparing(Task::getDeadline)
				.thenComparing(Task::getId);
	}

	// Relative wording only: the server doesn't know the user's time zone, and the app refetches on open
	public static String reason(Task task, Instant now) {
		if (PriorityEngine.isOverdue(task, now)) {
			return "Overdue by " + span(Duration.between(task.getDeadline(), now));
		}
		Duration left = Duration.between(now, task.getDeadline());
		String reason = "Due in " + span(left);
		// With a day or more to go, the amount of work is what helps plan when to start
		if (left.compareTo(PriorityEngine.DUE_TOMORROW_WINDOW) >= 0) {
			reason += ", about " + task.getEstimatedHours().stripTrailingZeros().toPlainString() + " h of work";
		}
		return reason;
	}

	private static int rank(Task task, Instant now) {
		if (PriorityEngine.isOverdue(task, now)) {
			return 0;
		}
		return switch (task.getCurrentPriority()) {
			case DUE_TOMORROW -> 1;
			case HIGH -> 2;
			case MEDIUM -> 3;
			case LOW -> 4;
		};
	}

	// Rounded down to the largest whole unit: 5 h 59 min -> "5 hours"
	static String span(Duration d) {
		if (d.toDays() >= 1) {
			return plural(d.toDays(), "day");
		}
		if (d.toHours() >= 1) {
			return plural(d.toHours(), "hour");
		}
		return plural(Math.max(1, d.toMinutes()), "minute");
	}

	private static String plural(long n, String unit) {
		return n + " " + unit + (n == 1 ? "" : "s");
	}

}
