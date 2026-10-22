package com.justdoit.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.entity.User;
import com.justdoit.backend.repository.TaskRepository;
import com.justdoit.backend.repository.UserRepository;

import jakarta.persistence.EntityManager;

// What the 15-minute scheduler does, run against the real schema and rolled back afterwards
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PriorityRecalculationTests {

	@Autowired
	private TaskService taskService;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private UserRepository users;

	@Autowired
	private EntityManager em;

	@Test
	void movesStalePrioritiesWithoutTouchingVersionOrCompletedTasks() {
		User user = new User();
		user.setUsername("scheduler_test");
		user.setFullName("Scheduler Test");
		user.setPasswordHash("not-used");
		users.save(user);

		Instant now = Instant.now();
		long nowDueTomorrow = seed(user, Priority.LOW, now.plus(Duration.ofHours(10)), TaskStatus.PENDING);
		long nowLow = seed(user, Priority.HIGH, now.plus(Duration.ofDays(20)), TaskStatus.PENDING);
		long alreadyRight = seed(user, Priority.LOW, now.plus(Duration.ofDays(20)), TaskStatus.PENDING);
		long completed = seed(user, Priority.LOW, now.plus(Duration.ofHours(2)), TaskStatus.COMPLETED);

		int changed = taskService.recalculatePriorities(now);
		em.clear(); // the bulk UPDATE bypassed the loaded entities; read fresh rows

		// Other committed tasks in the local DB may also move, so check these rows rather than an exact total
		assertThat(changed).isGreaterThanOrEqualTo(2);
		assertThat(reload(nowDueTomorrow).getCurrentPriority()).isEqualTo(Priority.DUE_TOMORROW);
		assertThat(reload(nowLow).getCurrentPriority()).isEqualTo(Priority.LOW);
		assertThat(reload(alreadyRight).getCurrentPriority()).isEqualTo(Priority.LOW);
		assertThat(reload(completed).getCurrentPriority()).as("completed tasks stay frozen").isEqualTo(Priority.LOW);

		// No version bump: the user's next edit from another device must not get a false conflict
		assertThat(reload(nowDueTomorrow).getVersion()).isZero();
		assertThat(reload(nowDueTomorrow).getInitialPriority()).isEqualTo(Priority.LOW);
	}

	private long seed(User user, Priority stored, Instant deadline, TaskStatus status) {
		Task task = new Task();
		task.setUser(user);
		task.setTitle("t");
		task.setCategory(Category.ASSIGNMENT);
		task.setEstimatedHours(BigDecimal.ONE);
		task.setDeadline(deadline);
		task.setInitialPriority(stored);
		task.setCurrentPriority(stored);
		task.setStatus(status);
		return tasks.saveAndFlush(task).getId();
	}

	private Task reload(long id) {
		return tasks.findById(id).orElseThrow();
	}

}
