package com.justdoit.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.entity.User;
import com.justdoit.backend.repository.TaskRepository;
import com.justdoit.backend.repository.UserRepository;
import com.justdoit.backend.service.JwtService;

// End-to-end through HTTP, Security and Postgres; rolled back after each test
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private UserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private JwtService jwtService;

	@Test
	void createComputesPriorityAndIgnoresClientPriority() throws Exception {
		String token = tokenFor(newUser("creator"));
		Map<String, Object> body = taskBody("Capstone report", Category.PROJECT, "10", in(Duration.ofDays(4)));
		body.put("initialPriority", "DUE_TOMORROW"); // must be ignored
		body.put("currentPriority", "DUE_TOMORROW");

		// 96 h left / 10 h = 9.6 -> LOW, PROJECT moves it up to MEDIUM
		send(post("/tasks"), token, body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.initialPriority").value("MEDIUM"))
				.andExpect(jsonPath("$.currentPriority").value("MEDIUM"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.estimatedHours").value(10))
				.andExpect(jsonPath("$.dueSoon").value(false))
				.andExpect(jsonPath("$.overdue").value(false));

		// Under 24 h left: DUE_TOMORROW straight away, no waiting for the scheduler
		send(post("/tasks"), token, taskBody("Sign form", Category.ADMINISTRATIVE, "0.5", in(Duration.ofHours(5))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.initialPriority").value("DUE_TOMORROW"))
				.andExpect(jsonPath("$.currentPriority").value("DUE_TOMORROW"))
				.andExpect(jsonPath("$.dueSoon").value(true));
	}

	@Test
	void createValidatesFields() throws Exception {
		String token = tokenFor(newUser("validator"));
		Map<String, Object> body = taskBody(" ", null, "2.55", in(Duration.ofHours(-1)));

		send(post("/tasks"), token, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"))
				.andExpect(jsonPath("$.fieldErrors.title").value("Enter a task name."))
				.andExpect(jsonPath("$.fieldErrors.category").value("Choose a category."))
				.andExpect(jsonPath("$.fieldErrors.estimatedHours").value("Use at most one decimal place."))
				.andExpect(jsonPath("$.fieldErrors.deadline").value("Pick a time in the future."));

		send(post("/tasks"), token, taskBody("Too long", Category.LAB, "1000", in(Duration.ofDays(1))))
				.andExpect(jsonPath("$.fieldErrors.estimatedHours").value("Use 0.1 to 999.9 hours."));
	}

	@Test
	void anotherUsersTaskLooksExactlyLikeAMissingOne() throws Exception {
		String owner = tokenFor(newUser("owner"));
		String intruder = tokenFor(newUser("intruder"));
		long id = createTask(owner, taskBody("Private", Category.PERSONAL, "1", in(Duration.ofDays(2))));

		String theirs = mvc.perform(get("/tasks/" + id).header("Authorization", "Bearer " + intruder))
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		String missing = mvc.perform(get("/tasks/999999999").header("Authorization", "Bearer " + intruder))
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		assertThat(theirs).isEqualTo(missing).contains("NOT_AVAILABLE");

		send(patch("/tasks/" + id), intruder, Map.of("title", "Mine now", "version", 0)).andExpect(status().isNotFound());
		mvc.perform(delete("/tasks/" + id).param("version", "0").header("Authorization", "Bearer " + intruder))
				.andExpect(status().isNotFound());
	}

	@Test
	void rescheduleRecalculatesCurrentPriorityButNeverInitial() throws Exception {
		String token = tokenFor(newUser("rescheduler"));
		// 240 h left / 10 h = 24 -> LOW
		long id = createTask(token, taskBody("Essay", Category.ASSIGNMENT, "10", in(Duration.ofDays(10))));

		// 48 h left / 10 h = 4.8 -> MEDIUM, applied in the same request
		send(patch("/tasks/" + id), token, Map.of("deadline", in(Duration.ofDays(2)), "version", 0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPriority").value("MEDIUM"))
				.andExpect(jsonPath("$.initialPriority").value("LOW"))
				.andExpect(jsonPath("$.version").value(1));

		// The same stale version again: another device already moved it on
		send(patch("/tasks/" + id), token, Map.of("deadline", in(Duration.ofDays(3)), "version", 0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));

		send(patch("/tasks/" + id), token, Map.of("deadline", in(Duration.ofHours(-2)), "version", 1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.deadline").value("Pick a time in the future."));
	}

	@Test
	void completeThenReopenWithTheReturnedVersion() throws Exception {
		String token = tokenFor(newUser("completer"));
		long id = createTask(token, taskBody("Quiz prep", Category.QUIZ, "2", in(Duration.ofDays(3))));

		send(post("/tasks/" + id + "/complete"), token, Map.of("version", 0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.version").value(1));

		// Completed tasks are read-only until reopened (SRS 9.5)
		send(patch("/tasks/" + id), token, Map.of("title", "Changed", "version", 1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TASK_COMPLETED"));

		// "Undo" uses the version the complete call returned
		send(post("/tasks/" + id + "/reopen"), token, Map.of("version", 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void deleteNeedsTheCurrentVersion() throws Exception {
		String token = tokenFor(newUser("deleter"));
		long id = createTask(token, taskBody("Old club meeting", Category.MEETING, "1", in(Duration.ofDays(5))));

		mvc.perform(delete("/tasks/" + id).param("version", "7").header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict());
		mvc.perform(delete("/tasks/" + id).param("version", "0").header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		mvc.perform(get("/tasks/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void listFiltersSortsPagesAndSummaryCountsAllPending() throws Exception {
		User user = newUser("lister");
		String token = tokenFor(user);
		Instant now = Instant.now();
		seed(user, "low-later", Priority.LOW, now.plus(Duration.ofDays(20)), TaskStatus.PENDING);
		seed(user, "high-soon", Priority.HIGH, now.plus(Duration.ofDays(2)), TaskStatus.PENDING);
		seed(user, "due-tomorrow", Priority.DUE_TOMORROW, now.plus(Duration.ofHours(10)), TaskStatus.PENDING);
		seed(user, "overdue", Priority.DUE_TOMORROW, now.minus(Duration.ofHours(3)), TaskStatus.PENDING);
		seed(user, "medium", Priority.MEDIUM, now.plus(Duration.ofDays(5)), TaskStatus.PENDING);
		seed(user, "done", Priority.HIGH, now.plus(Duration.ofDays(1)), TaskStatus.COMPLETED);
		seed(newUser("someone_else"), "not-mine", Priority.HIGH, now.plus(Duration.ofDays(1)), TaskStatus.PENDING);

		// Default sort: most urgent first, then soonest deadline
		list(token, "status=PENDING")
				.andExpect(jsonPath("$.items[*].title").value(contains("overdue", "due-tomorrow", "high-soon", "medium", "low-later")))
				.andExpect(jsonPath("$.hasNext").value(false));
		list(token, "status=PENDING&sort=DEADLINE")
				.andExpect(jsonPath("$.items[*].title").value(contains("overdue", "due-tomorrow", "high-soon", "medium", "low-later")));
		list(token, "status=PENDING&priority=HIGH")
				.andExpect(jsonPath("$.items[*].title").value(contains("high-soon")));
		list(token, "scope=OVERDUE")
				.andExpect(jsonPath("$.items[*].title").value(contains("overdue")))
				.andExpect(jsonPath("$.items[0].overdue").value(true));
		list(token, "scope=DUE_SOON")
				.andExpect(jsonPath("$.items[*].title").value(contains("due-tomorrow", "high-soon")));
		list(token, "status=COMPLETED")
				.andExpect(jsonPath("$.items[*].title").value(contains("done")));
		list(token, "priority=URGENT")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.priority").exists());

		mvc.perform(get("/tasks/summary").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pending.DUE_TOMORROW").value(2))
				.andExpect(jsonPath("$.pending.HIGH").value(1))
				.andExpect(jsonPath("$.pending.MEDIUM").value(1))
				.andExpect(jsonPath("$.pending.LOW").value(1))
				.andExpect(jsonPath("$.dueSoon").value(2))
				.andExpect(jsonPath("$.overdue").value(1));
	}

	@Test
	void listPagesTwentyAtATime() throws Exception {
		User user = newUser("pager");
		String token = tokenFor(user);
		for (int i = 0; i < 21; i++) {
			seed(user, "task-" + i, Priority.LOW, Instant.now().plus(Duration.ofDays(30 + i)), TaskStatus.PENDING);
		}
		list(token, "page=0").andExpect(jsonPath("$.items", hasSize(20))).andExpect(jsonPath("$.hasNext").value(true));
		list(token, "page=1")
				.andExpect(jsonPath("$.items[*].title").value(contains("task-20")))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	private User newUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setFullName("Task Tester");
		user.setPasswordHash("not-used-in-these-tests");
		return users.save(user);
	}

	private String tokenFor(User user) {
		return jwtService.issue(user);
	}

	private void seed(User user, String title, Priority priority, Instant deadline, TaskStatus status) {
		Task task = new Task();
		task.setUser(user);
		task.setTitle(title);
		task.setCategory(Category.ASSIGNMENT);
		task.setEstimatedHours(BigDecimal.ONE);
		task.setDeadline(deadline);
		task.setInitialPriority(priority);
		task.setCurrentPriority(priority);
		task.setStatus(status);
		tasks.save(task);
	}

	private static String in(Duration fromNow) {
		return OffsetDateTime.now().plus(fromNow).toString();
	}

	private static Map<String, Object> taskBody(String title, Category category, String hours, String deadline) {
		Map<String, Object> body = new HashMap<>();
		body.put("title", title);
		body.put("category", category);
		body.put("estimatedHours", new BigDecimal(hours));
		body.put("deadline", deadline);
		return body;
	}

	private long createTask(String token, Map<String, Object> body) throws Exception {
		String response = send(post("/tasks"), token, body).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(response).get("id").asLong();
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String token, Map<String, ?> body) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body)));
	}

	private ResultActions list(String token, String query) throws Exception {
		return mvc.perform(get("/tasks?" + query).header("Authorization", "Bearer " + token));
	}

}
