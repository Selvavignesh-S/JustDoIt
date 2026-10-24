package com.justdoit.backend.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
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
class PushServiceTests {

	private static final String PHONE = "ExponentPushToken[phone-1]";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient db;

	@Autowired
	private UserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private JwtService jwtService;

	// Records what would have gone to Expo; the scheduler's own PushService stays off in tests
	private final List<PushMessage> sent = new ArrayList<>();
	private final Set<String> uninstalled = new HashSet<>();
	private PushService push;
	private final Instant now = Instant.now();

	@BeforeEach
	void setUp() {
		push = new PushService(db, tasks, messages -> {
			sent.addAll(messages);
			return uninstalled;
		}, true);
	}

	@Test
	void pushesEachClimbOnceAndAgainAfterADrop() {
		User me = newUser("pushed");
		push.register(me.getId(), PHONE);
		Task essay = seed(me, "Essay", Priority.HIGH, now.plus(Duration.ofHours(60)));
		seed(me, "Relaxed", Priority.LOW, now.plus(Duration.ofDays(9)));

		push.pushDueReminders(now);
		assertThat(sent).singleElement().satisfies(m -> {
			assertThat(m.to()).isEqualTo(PHONE);
			assertThat(m.title()).isEqualTo("Essay");
			assertThat(m.body()).isEqualTo("Due in 2 days, about 2 h of work");
			assertThat(m.data()).containsEntry("taskId", essay.getId());
		});

		sent.clear();
		push.pushDueReminders(now);
		assertThat(sent).as("same stage: nothing new").isEmpty();

		essay.setCurrentPriority(Priority.DUE_TOMORROW);
		tasks.saveAndFlush(essay);
		push.pushDueReminders(now);
		assertThat(sent).as("climbed to Due tomorrow").hasSize(1);

		// Rescheduled far away (Low), then urgent again: pushed again
		sent.clear();
		essay.setCurrentPriority(Priority.LOW);
		essay.setDeadline(now.plus(Duration.ofDays(9)));
		tasks.saveAndFlush(essay);
		push.pushDueReminders(now);
		assertThat(sent).isEmpty();
		essay.setCurrentPriority(Priority.HIGH);
		tasks.saveAndFlush(essay);
		push.pushDueReminders(now);
		assertThat(sent).hasSize(1);
	}

	@Test
	void severalAtOnceBecomeOneSummary() {
		User me = newUser("busy");
		push.register(me.getId(), PHONE);
		seed(me, "Lab", Priority.DUE_TOMORROW, now.plus(Duration.ofMinutes(330)));
		seed(me, "Late essay", Priority.HIGH, now.minus(Duration.ofMinutes(150)));

		push.pushDueReminders(now);

		assertThat(sent).singleElement().satisfies(m -> {
			assertThat(m.title()).isEqualTo("2 tasks need attention");
			assertThat(m.body()).isEqualTo("Late essay: Overdue by 2 hours\nLab: Due in 5 hours");
			assertThat(m.data()).isEmpty();
		});
	}

	@Test
	void forgetsUninstalledPhonesAndSkipsUsersWithoutPhones() {
		User me = newUser("gone_phone");
		push.register(me.getId(), PHONE);
		seed(me, "Essay", Priority.HIGH, now.plus(Duration.ofHours(60)));
		seed(newUser("no_phone"), "Theirs", Priority.HIGH, now.plus(Duration.ofHours(60)));
		uninstalled.add(PHONE);

		push.pushDueReminders(now);

		assertThat(sent).extracting(PushMessage::title).containsExactly("Essay");
		assertThat(tokensOf(me)).isEmpty();
	}

	@Test
	void aPhoneBelongsToTheLastAccountThatSignedIn() {
		User first = newUser("first_owner");
		User second = newUser("second_owner");
		push.register(first.getId(), PHONE);
		push.register(second.getId(), PHONE);

		assertThat(tokensOf(first)).isEmpty();
		assertThat(tokensOf(second)).containsExactly(PHONE);
	}

	@Test
	void tokenEndpointsCheckTheShapeAndOwner() throws Exception {
		User me = newUser("api_phone");
		String auth = "Bearer " + jwtService.issue(me);

		mvc.perform(put("/users/me/push-token").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"https://evil.example/hook\"}")).andExpect(status().isBadRequest());
		mvc.perform(put("/users/me/push-token").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + PHONE + "\"}")).andExpect(status().isNoContent());
		assertThat(tokensOf(me)).containsExactly(PHONE);

		// Someone else can't remove my phone
		mvc.perform(delete("/users/me/push-token").header("Authorization", "Bearer " + jwtService.issue(newUser("stranger")))
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + PHONE + "\"}")).andExpect(status().isNoContent());
		assertThat(tokensOf(me)).containsExactly(PHONE);

		mvc.perform(delete("/users/me/push-token").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + PHONE + "\"}")).andExpect(status().isNoContent());
		assertThat(tokensOf(me)).isEmpty();
		mvc.perform(put("/users/me/push-token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + PHONE + "\"}")).andExpect(status().isUnauthorized());
	}

	private List<String> tokensOf(User user) {
		return db.sql("SELECT token FROM push_tokens WHERE user_id = :user").param("user", user.getId()).query(String.class).list();
	}

	private User newUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setFullName("Push Tester");
		user.setPasswordHash("not-used");
		return users.saveAndFlush(user);
	}

	private Task seed(User user, String title, Priority priority, Instant deadline) {
		Task task = new Task();
		task.setUser(user);
		task.setTitle(title);
		task.setCategory(Category.ASSIGNMENT);
		task.setEstimatedHours(new BigDecimal("2"));
		task.setDeadline(deadline);
		task.setInitialPriority(priority);
		task.setCurrentPriority(priority);
		task.setStatus(TaskStatus.PENDING);
		return tasks.saveAndFlush(task);
	}

}
