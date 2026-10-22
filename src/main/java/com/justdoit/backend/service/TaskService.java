package com.justdoit.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.justdoit.backend.dto.TaskCreateRequest;
import com.justdoit.backend.dto.TaskSummaryResponse;
import com.justdoit.backend.dto.TaskUpdateRequest;
import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskScope;
import com.justdoit.backend.entity.TaskSort;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.exception.ApiException;
import com.justdoit.backend.repository.TaskRepository;
import com.justdoit.backend.repository.UserRepository;

import jakarta.persistence.criteria.Predicate;

// Every method takes the user id from the JWT and checks ownership before anything else (Route plan section 8)
@Service
public class TaskService {

	private static final int PAGE_SIZE = 20;
	private static final int RECALC_CHUNK = 500;

	private final TaskRepository tasks;
	private final UserRepository users;

	public TaskService(TaskRepository tasks, UserRepository users) {
		this.tasks = tasks;
		this.users = users;
	}

	@Transactional
	public Task create(long userId, TaskCreateRequest req, Instant now) {
		Task task = new Task();
		task.setUser(users.getReferenceById(userId));
		task.setTitle(req.title().trim());
		task.setDescription(blankToNull(req.description()));
		task.setCategory(req.category());
		task.setEstimatedHours(req.estimatedHours());
		task.setDeadline(req.deadline().toInstant());
		// Priority comes only from the rule engine, never from the client or the AI (SRS 11)
		Priority initial = PriorityEngine.current(task, now);
		task.setInitialPriority(initial);
		task.setCurrentPriority(initial);
		task.setStatus(TaskStatus.PENDING);
		return tasks.saveAndFlush(task);
	}

	@Transactional(readOnly = true)
	public Task get(long userId, long taskId) {
		return owned(userId, taskId);
	}

	@Transactional(readOnly = true)
	public Page<Task> list(long userId, TaskStatus status, Priority priority, Category category, TaskScope scope,
			TaskSort sort, int page, Instant now) {
		Specification<Task> query = filter(userId, status, priority, category, scope, now).and(orderBy(sort));
		return tasks.findAll(query, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
	}

	@Transactional(readOnly = true)
	public TaskSummaryResponse summary(long userId, Instant now) {
		Map<Priority, Long> pending = new EnumMap<>(Priority.class);
		for (Priority p : Priority.values()) {
			pending.put(p, tasks.count(filter(userId, TaskStatus.PENDING, p, null, null, now)));
		}
		return new TaskSummaryResponse(pending,
				tasks.count(filter(userId, null, null, null, TaskScope.DUE_SOON, now)),
				tasks.count(filter(userId, null, null, null, TaskScope.OVERDUE, now)));
	}

	// Edit and reschedule (Route plan 3.6, SRS 9.5)
	@Transactional
	public Task update(long userId, long taskId, TaskUpdateRequest req, Instant now) {
		Task task = owned(userId, taskId);
		checkVersion(task, req.version());
		if (task.getStatus() == TaskStatus.COMPLETED) {
			throw ApiException.taskCompleted();
		}
		if (req.title() != null) {
			task.setTitle(req.title().trim());
		}
		if (req.description() != null) {
			task.setDescription(blankToNull(req.description()));
		}
		boolean urgencyInputsChanged = false;
		if (req.category() != null && req.category() != task.getCategory()) {
			task.setCategory(req.category());
			urgencyInputsChanged = true;
		}
		if (req.estimatedHours() != null && req.estimatedHours().compareTo(task.getEstimatedHours()) != 0) {
			task.setEstimatedHours(req.estimatedHours());
			urgencyInputsChanged = true;
		}
		if (req.deadline() != null && !req.deadline().toInstant().equals(task.getDeadline())) {
			task.setDeadline(req.deadline().toInstant());
			urgencyInputsChanged = true;
		}
		// Recalculate now rather than waiting for the scheduler (SRS 9.5); initialPriority never changes
		if (urgencyInputsChanged) {
			task.setCurrentPriority(PriorityEngine.current(task, now));
		}
		return tasks.saveAndFlush(task);
	}

	@Transactional
	public Task complete(long userId, long taskId, int version) {
		Task task = owned(userId, taskId);
		checkVersion(task, version);
		// currentPriority is left frozen: completed tasks stop escalating (SRS 11)
		task.setStatus(TaskStatus.COMPLETED);
		return tasks.saveAndFlush(task);
	}

	@Transactional
	public Task reopen(long userId, long taskId, int version, Instant now) {
		Task task = owned(userId, taskId);
		checkVersion(task, version);
		task.setStatus(TaskStatus.PENDING);
		// Time has passed while it was completed, so its urgency is re-evaluated straight away
		task.setCurrentPriority(PriorityEngine.current(task, now));
		return tasks.saveAndFlush(task);
	}

	// Allowed in either status (SRS 9.5). The DB cascade removes attachments and versions.
	// The attachments step must also delete their stored files here.
	@Transactional
	public void delete(long userId, long taskId, int version) {
		Task task = owned(userId, taskId);
		checkVersion(task, version);
		tasks.delete(task);
		tasks.flush();
	}

	// The scheduler's job (SRS 12): re-run the rule on every pending task and store only what changed.
	// Returns how many tasks moved. Completed tasks are never touched, so they stay frozen.
	// shortcut: one transaction over all pending tasks of all users; fine for V1 volumes, split per chunk if it grows slow.
	@Transactional
	public int recalculatePriorities(Instant now) {
		int changed = 0;
		Pageable chunk = PageRequest.of(0, RECALC_CHUNK, Sort.by("id"));
		Slice<Task> slice;
		do {
			slice = tasks.findByStatus(TaskStatus.PENDING, chunk);
			Map<Priority, List<Long>> moves = new EnumMap<>(Priority.class);
			for (Task task : slice) {
				Priority recalculated = PriorityEngine.current(task, now);
				if (recalculated != task.getCurrentPriority()) {
					moves.computeIfAbsent(recalculated, p -> new ArrayList<>()).add(task.getId());
				}
			}
			for (Map.Entry<Priority, List<Long>> move : moves.entrySet()) {
				changed += tasks.updateCurrentPriority(move.getValue(), move.getKey(), TaskStatus.PENDING);
			}
			chunk = slice.nextPageable();
		}
		while (slice.hasNext());
		return changed;
	}

	private Task owned(long userId, long taskId) {
		return tasks.findByIdAndUserId(taskId, userId).orElseThrow(ApiException::notAvailable);
	}

	// The client must have seen the latest row; @Version also catches the rare race between this check and the save
	private static void checkVersion(Task task, int clientVersion) {
		if (task.getVersion() != clientVersion) {
			throw ApiException.conflict();
		}
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

	private static Specification<Task> filter(long userId, TaskStatus status, Priority priority, Category category,
			TaskScope scope, Instant now) {
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			where.add(cb.equal(root.get("user").get("id"), userId));
			if (status != null) {
				where.add(cb.equal(root.get("status"), status));
			}
			if (priority != null) {
				where.add(cb.equal(root.get("currentPriority"), priority));
			}
			if (category != null) {
				where.add(cb.equal(root.get("category"), category));
			}
			// Due soon and Overdue are subsets of Pending (UI plan 7.5)
			if (scope == TaskScope.OVERDUE) {
				where.add(cb.equal(root.get("status"), TaskStatus.PENDING));
				where.add(cb.lessThan(root.get("deadline"), now));
			}
			else if (scope == TaskScope.DUE_SOON) {
				where.add(cb.equal(root.get("status"), TaskStatus.PENDING));
				where.add(cb.greaterThanOrEqualTo(root.get("deadline"), now));
				where.add(cb.lessThan(root.get("deadline"), now.plus(PriorityEngine.DUE_SOON_WINDOW)));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	// Priorities are stored as text, so "most urgent first" needs an explicit rank (SRS 18.6)
	private static Specification<Task> orderBy(TaskSort sort) {
		return (root, query, cb) -> {
			// Spring Data reuses this for the page's count query, where ORDER BY isn't allowed
			if (Long.class.equals(query.getResultType())) {
				return null;
			}
			var deadline = cb.asc(root.get("deadline"));
			var id = cb.asc(root.get("id"));
			if (sort == TaskSort.DEADLINE) {
				query.orderBy(deadline, id);
			}
			else {
				var rank = cb.selectCase(root.get("currentPriority"))
						.when(Priority.DUE_TOMORROW, 0)
						.when(Priority.HIGH, 1)
						.when(Priority.MEDIUM, 2)
						.otherwise(3);
				query.orderBy(cb.asc(rank), deadline, id);
			}
			return null;
		};
	}

}
