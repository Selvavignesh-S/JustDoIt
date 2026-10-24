package com.justdoit.backend.push;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.repository.TaskRepository;
import com.justdoit.backend.service.ReminderPolicy;

// Reminders outside the app (decided 2026-10-25). The same policy as GET /reminders (SRS 13), pushed once each
// time a task climbs to a more urgent stage: Medium due soon, then High, then Due tomorrow, then Overdue.
@Service
public class PushService {

	private static final Logger log = LoggerFactory.getLogger(PushService.class);

	// Enough for a phone and a tablet plus reinstalls; older tokens beyond this are dropped
	static final int MAX_TOKENS_PER_USER = 5;

	private final JdbcClient db;
	private final TaskRepository tasks;
	private final PushSender sender;
	private final boolean enabled;

	public PushService(JdbcClient db, TaskRepository tasks, PushSender sender, @Value("${push.enabled}") boolean enabled) {
		this.db = db;
		this.tasks = tasks;
		this.sender = sender;
		this.enabled = enabled;
	}

	// A phone that signs in to another account moves to it, so one phone never gets two people's reminders
	public void register(long userId, String token) {
		db.sql("""
				INSERT INTO push_tokens (token, user_id) VALUES (:token, :user)
				ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, created_at = CURRENT_TIMESTAMP""")
				.param("token", token).param("user", userId).update();
		db.sql("""
				DELETE FROM push_tokens WHERE user_id = :user AND token NOT IN
				(SELECT token FROM push_tokens WHERE user_id = :user ORDER BY created_at DESC LIMIT :max)""")
				.param("user", userId).param("max", MAX_TOKENS_PER_USER).update();
	}

	// Only the owner can remove a token (on log out)
	public void unregister(long userId, String token) {
		db.sql("DELETE FROM push_tokens WHERE token = :token AND user_id = :user").param("token", token).param("user", userId).update();
	}

	// Runs after every priority recalculation (PriorityScheduler)
	public void pushDueReminders(Instant now) {
		if (!enabled) {
			return;
		}
		List<Long> users = db.sql("SELECT DISTINCT user_id FROM push_tokens").query(Long.class).list();
		int sent = 0;
		for (long userId : users) {
			try {
				sent += pushFor(userId, now);
			}
			catch (RuntimeException e) {
				// Nothing was recorded for this user, so the same reminders are tried again next run
				log.warn("Push reminders failed for user {}: {}", userId, e.toString());
			}
		}
		if (sent > 0) {
			log.info("Push reminders: {} notification(s) sent", sent);
		}
	}

	private int pushFor(long userId, Instant now) {
		List<Task> pending = tasks.findByUserIdAndStatus(userId, TaskStatus.PENDING);
		Map<Long, Integer> pushed = db.sql("SELECT r.task_id, r.stage FROM reminder_pushes r JOIN tasks t ON t.task_id = r.task_id WHERE t.user_id = :user")
				.param("user", userId)
				.query((rs, n) -> Map.entry(rs.getLong("task_id"), rs.getInt("stage")))
				.list().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		List<Task> climbed = new ArrayList<>();
		Map<Long, Integer> stages = new HashMap<>();
		for (Task task : pending) {
			int stage = ReminderPolicy.stage(task, now);
			int before = pushed.getOrDefault(task.getId(), 0);
			stages.put(task.getId(), stage);
			if (stage > before) {
				climbed.add(task);
			}
		}

		if (!climbed.isEmpty()) {
			climbed.sort(ReminderPolicy.order(now));
			List<String> tokens = db.sql("SELECT token FROM push_tokens WHERE user_id = :user").param("user", userId).query(String.class).list();
			PushMessage template = message(climbed, now);
			Set<String> gone = sender.send(tokens.stream()
					.map(t -> new PushMessage(t, template.title(), template.body(), template.data()))
					.toList());
			gone.forEach(t -> db.sql("DELETE FROM push_tokens WHERE token = :token").param("token", t).update());
		}

		// Record every stage, including drops: a task rescheduled to Low is pushed again if it climbs back up.
		// Completed tasks' rows go, so a reopened task starts fresh.
		Set<Long> pendingIds = stages.keySet();
		pushed.keySet().stream().filter(id -> !pendingIds.contains(id))
				.forEach(id -> db.sql("DELETE FROM reminder_pushes WHERE task_id = :id").param("id", id).update());
		stages.forEach((id, stage) -> {
			if (stage == 0) {
				db.sql("DELETE FROM reminder_pushes WHERE task_id = :id").param("id", id).update();
			}
			else if (stage != pushed.getOrDefault(id, 0)) {
				db.sql("""
						INSERT INTO reminder_pushes (task_id, stage) VALUES (:id, :stage)
						ON CONFLICT (task_id) DO UPDATE SET stage = EXCLUDED.stage, pushed_at = CURRENT_TIMESTAMP""")
						.param("id", id).param("stage", stage).update();
			}
		});
		return climbed.isEmpty() ? 0 : 1;
	}

	// One task: its title and reason, and tapping opens it. Several at once: one summary that opens the app.
	static PushMessage message(List<Task> climbed, Instant now) {
		if (climbed.size() == 1) {
			Task task = climbed.get(0);
			return new PushMessage(null, task.getTitle(), ReminderPolicy.reason(task, now), Map.of("taskId", task.getId()));
		}
		String body = climbed.stream().limit(3)
				.map(t -> t.getTitle() + ": " + ReminderPolicy.reason(t, now))
				.collect(Collectors.joining("\n"));
		if (climbed.size() > 3) {
			body += "\nand " + (climbed.size() - 3) + " more";
		}
		return new PushMessage(null, climbed.size() + " tasks need attention", body, Map.of());
	}

}
