package com.justdoit.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;

class ReminderPolicyTests {

	private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

	private static Task task(String title, Priority priority, Duration untilDeadline, String hours, TaskStatus status) {
		Task task = new Task();
		task.setTitle(title);
		task.setCategory(Category.ASSIGNMENT);
		task.setCurrentPriority(priority);
		task.setInitialPriority(priority);
		task.setDeadline(NOW.plus(untilDeadline));
		task.setEstimatedHours(new BigDecimal(hours));
		task.setStatus(status);
		return task;
	}

	private static Task pending(Priority priority, Duration untilDeadline) {
		return task("t", priority, untilDeadline, "1", TaskStatus.PENDING);
	}

	@Test
	void whoGetsReminded() {
		assertThat(ReminderPolicy.qualifies(pending(Priority.DUE_TOMORROW, Duration.ofHours(10)), NOW)).isTrue();
		assertThat(ReminderPolicy.qualifies(pending(Priority.HIGH, Duration.ofDays(6)), NOW)).isTrue();
		assertThat(ReminderPolicy.qualifies(pending(Priority.MEDIUM, Duration.ofHours(71)), NOW)).isTrue();
		assertThat(ReminderPolicy.qualifies(pending(Priority.MEDIUM, Duration.ofHours(72)), NOW)).as("outside 3 days").isFalse();
		assertThat(ReminderPolicy.qualifies(pending(Priority.LOW, Duration.ofDays(2)), NOW)).isFalse();
		assertThat(ReminderPolicy.qualifies(pending(Priority.LOW, Duration.ofHours(-1)), NOW)).as("overdue, scheduler not run yet").isTrue();
		assertThat(ReminderPolicy.qualifies(task("t", Priority.HIGH, Duration.ofHours(5), "1", TaskStatus.COMPLETED), NOW)).isFalse();
	}

	@Test
	void reasonsUseRelativeTime() {
		assertThat(ReminderPolicy.reason(pending(Priority.DUE_TOMORROW, Duration.ofHours(-3)), NOW)).isEqualTo("Overdue by 3 hours");
		assertThat(ReminderPolicy.reason(pending(Priority.DUE_TOMORROW, Duration.ofDays(-2)), NOW)).isEqualTo("Overdue by 2 days");
		assertThat(ReminderPolicy.reason(pending(Priority.DUE_TOMORROW, Duration.ofMinutes(359)), NOW)).isEqualTo("Due in 5 hours");
		assertThat(ReminderPolicy.reason(pending(Priority.DUE_TOMORROW, Duration.ofMinutes(40)), NOW)).isEqualTo("Due in 40 minutes");
		assertThat(ReminderPolicy.reason(pending(Priority.DUE_TOMORROW, Duration.ofHours(1)), NOW)).isEqualTo("Due in 1 hour");
		assertThat(ReminderPolicy.reason(task("t", Priority.HIGH, Duration.ofDays(2), "6.0", TaskStatus.PENDING), NOW))
				.isEqualTo("Due in 2 days, about 6 h of work");
		assertThat(ReminderPolicy.reason(task("t", Priority.HIGH, Duration.ofDays(1), "2.5", TaskStatus.PENDING), NOW))
				.isEqualTo("Due in 1 day, about 2.5 h of work");
	}

	@Test
	void overdueFirstThenUrgencyThenDeadline() {
		Task medium = task("medium", Priority.MEDIUM, Duration.ofHours(30), "1", TaskStatus.PENDING);
		Task highLater = task("high-later", Priority.HIGH, Duration.ofDays(5), "1", TaskStatus.PENDING);
		Task highSooner = task("high-sooner", Priority.HIGH, Duration.ofDays(2), "1", TaskStatus.PENDING);
		Task dueTomorrow = task("due-tomorrow", Priority.DUE_TOMORROW, Duration.ofHours(8), "1", TaskStatus.PENDING);
		Task overdue = task("overdue", Priority.DUE_TOMORROW, Duration.ofHours(-1), "1", TaskStatus.PENDING);

		List<String> order = List.of(medium, highLater, highSooner, dueTomorrow, overdue).stream()
				.sorted(ReminderPolicy.order(NOW)).map(Task::getTitle).toList();

		assertThat(order).containsExactly("overdue", "due-tomorrow", "high-sooner", "high-later", "medium");
	}

}
