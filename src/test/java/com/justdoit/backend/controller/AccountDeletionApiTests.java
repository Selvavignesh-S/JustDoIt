package com.justdoit.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.entity.User;
import com.justdoit.backend.repository.AttachmentVersionRepository;
import com.justdoit.backend.repository.TaskRepository;
import com.justdoit.backend.repository.UserRepository;
import com.justdoit.backend.service.JwtService;
import com.justdoit.backend.storage.FileStorage;
import com.justdoit.backend.storage.StorageException;

// Not @Transactional, like AttachmentApiTests: the delete commits for real so the stored files can be checked on disk
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionApiTests {

	private static final String PASSWORD = "correct horse 42";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient db;

	@Autowired
	private UserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private AttachmentVersionRepository versions;

	@Autowired
	private FileStorage storage;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	private final List<Long> createdUsers = new ArrayList<>();

	@AfterEach
	void deleteCreatedUsers() {
		users.deleteAllById(createdUsers.stream().filter(users::existsById).toList());
	}

	@Test
	void wrongPasswordDeletesNothing() throws Exception {
		User me = newUser("delete_wrong_pw");
		String token = jwtService.issue(me);

		deleteAccount(token, "not my password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
		deleteAccount(token, "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password").value("Enter your password."));
		assertThat(users.existsById(me.getId())).isTrue();
	}

	@Test
	void deletesTheAccountAndEverythingInItButNobodyElses() throws Exception {
		User me = newUser("delete_me");
		String token = jwtService.issue(me);
		long myTask = newTask(me);
		upload(token, myTask);
		registerPhone(token, "ExponentPushToken[delete-me]");
		List<String> myFiles = versions.storedFileNamesForUser(me.getId());
		assertThat(myFiles).hasSize(1);

		User other = newUser("delete_bystander");
		String otherToken = jwtService.issue(other);
		long otherTask = newTask(other);
		upload(otherToken, otherTask);
		registerPhone(otherToken, "ExponentPushToken[bystander]");
		List<String> otherFiles = versions.storedFileNamesForUser(other.getId());

		deleteAccount(token, PASSWORD).andExpect(status().isNoContent());

		assertThat(users.existsById(me.getId())).isFalse();
		assertThat(tasks.existsById(myTask)).isFalse();
		assertThat(pushTokens(me)).isZero();
		myFiles.forEach(key -> assertThatThrownBy(() -> storage.get(key).content().close()).isInstanceOf(StorageException.class));
		// The old token now belongs to nobody
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());

		assertThat(tasks.existsById(otherTask)).isTrue();
		assertThat(pushTokens(other)).isOne();
		assertThat(versions.storedFileNamesForUser(other.getId())).isEqualTo(otherFiles);
		storage.get(otherFiles.get(0)).content().close();
	}

	private ResultActions deleteAccount(String token, String password) throws Exception {
		return mvc.perform(delete("/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + password + "\"}"));
	}

	private void registerPhone(String token, String pushToken) throws Exception {
		mvc.perform(put("/users/me/push-token").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + pushToken + "\"}"))
				.andExpect(status().isNoContent());
	}

	private long pushTokens(User user) {
		return db.sql("select count(*) from push_tokens where user_id = ?").param(user.getId()).query(Long.class).single();
	}

	private void upload(String token, long taskId) throws Exception {
		var file = new MockMultipartFile("file", "notes.pdf", "application/pdf", "%PDF-1.7\nnotes".getBytes());
		mvc.perform(multipart("/tasks/" + taskId + "/attachments").file(file).header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated());
	}

	private User newUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setFullName("Deletion Tester");
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user = users.save(user);
		createdUsers.add(user.getId());
		return user;
	}

	private long newTask(User user) {
		Task task = new Task();
		task.setUser(user);
		task.setTitle("Lab report");
		task.setCategory(Category.ASSIGNMENT);
		task.setEstimatedHours(BigDecimal.ONE);
		task.setDeadline(Instant.now().plus(Duration.ofDays(3)));
		task.setInitialPriority(Priority.LOW);
		task.setCurrentPriority(Priority.LOW);
		task.setStatus(TaskStatus.PENDING);
		return tasks.save(task).getId();
	}

}
