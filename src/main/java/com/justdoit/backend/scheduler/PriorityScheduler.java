package com.justdoit.backend.scheduler;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.justdoit.backend.push.PushService;
import com.justdoit.backend.service.TaskService;

// SRS 12: keeps currentPriority in step with time. Runs once at startup (Render may have been asleep)
// and then every 15 minutes, so a task crosses into DUE_TOMORROW at most 15 minutes late.
@Component
public class PriorityScheduler {

	private static final Logger log = LoggerFactory.getLogger(PriorityScheduler.class);

	private final TaskService tasks;
	private final PushService push;

	public PriorityScheduler(TaskService tasks, PushService push) {
		this.tasks = tasks;
		this.push = push;
	}

	@Scheduled(initialDelay = 0, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
	public void recalculatePriorities() {
		long started = System.currentTimeMillis();
		try {
			int changed = tasks.recalculatePriorities(Instant.now());
			log.info("Priority recalculation: {} task(s) changed in {} ms", changed, System.currentTimeMillis() - started);
		}
		catch (RuntimeException e) {
			// Log and wait for the next run; an exception here would otherwise only show up as a stack trace
			log.error("Priority recalculation failed", e);
		}
		// After the recalculation, so pushes follow the fresh priorities
		try {
			push.pushDueReminders(Instant.now());
		}
		catch (RuntimeException e) {
			log.error("Push reminders failed", e);
		}
	}

}
