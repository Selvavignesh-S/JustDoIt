package com.justdoit.backend.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.justdoit.backend.dto.ReminderResponse;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.repository.TaskRepository;

// Reminders are rebuilt from scratch on every call: nothing is stored, so there is nothing to dismiss (Route plan 4.1)
@Service
public class ReminderService {

	private final TaskRepository tasks;

	public ReminderService(TaskRepository tasks) {
		this.tasks = tasks;
	}

	// shortcut: loads all of the user's pending tasks and filters in Java; fine for a student's task list,
	// move the policy into the query if users ever have thousands of pending tasks
	@Transactional(readOnly = true)
	public List<ReminderResponse> forUser(long userId, Instant now) {
		return tasks.findByUserIdAndStatus(userId, TaskStatus.PENDING).stream()
				.filter(task -> ReminderPolicy.qualifies(task, now))
				.sorted(ReminderPolicy.order(now))
				.map(task -> new ReminderResponse(task.getId(), task.getTitle(), ReminderPolicy.reason(task, now)))
				.toList();
	}

}
