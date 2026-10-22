package com.justdoit.backend.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.entity.User;
import com.justdoit.backend.repository.TaskRepository;
import com.justdoit.backend.repository.UserRepository;
import com.justdoit.backend.service.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReminderApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private JwtService jwtService;

	@Test
	void listsOnlyMyTasksThatNeedAttentionMostUrgentFirst() throws Exception {
		User me = newUser("reminded");
		Instant now = Instant.now();
		// Deadlines sit mid-unit so the rounded-down wording can't flip between seeding and the request
		seed(me, "overdue essay", Priority.DUE_TOMORROW, now.minus(Duration.ofMinutes(150)), "2", TaskStatus.PENDING);
		seed(me, "lab tonight", Priority.DUE_TOMORROW, now.plus(Duration.ofMinutes(330)), "1", TaskStatus.PENDING);
		seed(me, "big project", Priority.HIGH, now.plus(Duration.ofHours(60)), "20", TaskStatus.PENDING);
		seed(me, "quiz soon", Priority.MEDIUM, now.plus(Duration.ofHours(36)), "1.5", TaskStatus.PENDING);
		seed(me, "far medium", Priority.MEDIUM, now.plus(Duration.ofDays(6)), "1", TaskStatus.PENDING);
		seed(me, "relaxed", Priority.LOW, now.plus(Duration.ofDays(10)), "1", TaskStatus.PENDING);
		seed(me, "already done", Priority.DUE_TOMORROW, now.plus(Duration.ofHours(3)), "1", TaskStatus.COMPLETED);
		seed(newUser("other_person"), "not mine", Priority.DUE_TOMORROW, now.plus(Duration.ofHours(3)), "1", TaskStatus.PENDING);

		mvc.perform(get("/reminders").header("Authorization", "Bearer " + jwtService.issue(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].title").value(contains("overdue essay", "lab tonight", "big project", "quiz soon")))
				.andExpect(jsonPath("$[0].reason").value("Overdue by 2 hours"))
				.andExpect(jsonPath("$[1].reason").value("Due in 5 hours"))
				.andExpect(jsonPath("$[2].reason").value("Due in 2 days, about 20 h of work"))
				.andExpect(jsonPath("$[3].reason").value("Due in 1 day, about 1.5 h of work"))
				.andExpect(jsonPath("$[0].taskId").isNumber());
	}

	@Test
	void requiresLogin() throws Exception {
		mvc.perform(get("/reminders")).andExpect(status().isUnauthorized());
	}

	private User newUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setFullName("Reminder Tester");
		user.setPasswordHash("not-used");
		return users.save(user);
	}

	private void seed(User user, String title, Priority priority, Instant deadline, String hours, TaskStatus status) {
		Task task = new Task();
		task.setUser(user);
		task.setTitle(title);
		task.setCategory(Category.ASSIGNMENT);
		task.setEstimatedHours(new BigDecimal(hours));
		task.setDeadline(deadline);
		task.setInitialPriority(priority);
		task.setCurrentPriority(priority);
		task.setStatus(status);
		tasks.save(task);
	}

}
