package com.justdoit.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.service.PriorityEngine;

// overdue / dueSoon come from the server clock, so a phone with the wrong time can't change them (Route plan 3.3)
public record TaskResponse(
		long id,
		String title,
		String description,
		Category category,
		Instant deadline,
		BigDecimal estimatedHours,
		Priority initialPriority,
		Priority currentPriority,
		TaskStatus status,
		boolean overdue,
		boolean dueSoon,
		int version,
		Instant createdAt,
		Instant updatedAt) {

	public static TaskResponse from(Task task, Instant now) {
		return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getCategory(),
				task.getDeadline(), task.getEstimatedHours(), task.getInitialPriority(), task.getCurrentPriority(),
				task.getStatus(), PriorityEngine.isOverdue(task, now), PriorityEngine.isDueSoon(task, now),
				task.getVersion(), task.getCreatedAt(), task.getUpdatedAt());
	}

}
