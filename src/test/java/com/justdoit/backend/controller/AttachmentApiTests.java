package com.justdoit.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

// Not @Transactional on purpose: each request commits for real, so "files are deleted after commit" can be
// checked on disk (target/test-storage). The users created here are deleted after each test (cascading).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentApiTests {

	private static final byte[] PDF_V1 = "%PDF-1.7\nfirst draft".getBytes();
	private static final byte[] PDF_V2 = "%PDF-1.7\nsecond draft".getBytes();
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private UserRepository users;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private AttachmentVersionRepository versions;

	@Autowired
	private FileStorage storage;

	@Autowired
	private JwtService jwtService;

	private final List<Long> createdUsers = new ArrayList<>();

	@AfterEach
	void deleteCreatedUsers() {
		users.deleteAllById(createdUsers);
	}

	@Test
	void fullFileLifecycle() throws Exception {
		User me = newUser("files_owner");
		String token = jwtService.issue(me);
		long taskId = newTask(me);

		// Upload: version 1 becomes current
		JsonNode file = body(upload(token, taskId, pdf("DBMS draft.pdf", PDF_V1)).andExpect(status().isCreated()));
		long attachmentId = file.get("id").asLong();
		assertThat(file.get("displayName").asText()).isEqualTo("DBMS draft.pdf");
		assertThat(file.get("currentVersionNumber").asInt()).isEqualTo(1);
		long v1 = file.get("currentVersionId").asLong();
		int rowVersion = file.get("version").asInt();

		mvc.perform(get("/tasks/" + taskId).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.attachments[0].displayName").value("DBMS draft.pdf"))
				.andExpect(jsonPath("$.attachments[0].currentVersionId").value(v1));
		mvc.perform(get("/tasks").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.items[0].attachments").doesNotExist());

		// New version: never overwrites, becomes current, row version goes up
		file = body(uploadVersion(token, attachmentId, pdf("DBMS final.pdf", PDF_V2), "added ER diagram", rowVersion)
				.andExpect(status().isCreated()));
		assertThat(file.get("currentVersionNumber").asInt()).isEqualTo(2);
		long v2 = file.get("currentVersionId").asLong();
		assertThat(file.get("version").asInt()).isGreaterThan(rowVersion);
		uploadVersion(token, attachmentId, pdf("stale.pdf", PDF_V2), null, rowVersion)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This file changed on another device."));
		rowVersion = file.get("version").asInt();

		mvc.perform(get("/attachments/" + attachmentId + "/versions").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.attachmentVersion").value(rowVersion))
				.andExpect(jsonPath("$.versions[*].versionNumber").value(contains(2, 1)))
				.andExpect(jsonPath("$.versions[0].current").value(true))
				.andExpect(jsonPath("$.versions[0].changeDescription").value("added ER diagram"))
				.andExpect(jsonPath("$.versions[1].originalFileName").value("DBMS draft.pdf"));

		// Old version still downloadable, byte for byte
		mvc.perform(get("/versions/" + v1 + "/download").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PDF_V1))
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''DBMS%20draft.pdf")));

		// Restore v1: only the pointer moves, v2 stays
		file = body(sendVersion(post("/versions/" + v1 + "/restore"), token, rowVersion).andExpect(status().isOk()));
		assertThat(file.get("currentVersionId").asLong()).isEqualTo(v1);
		rowVersion = file.get("version").asInt();

		// Delete v2 (not current): current unchanged, row version bumped, its stored file gone
		String v2Key = versions.findById(v2).orElseThrow().getStoredFileName();
		file = body(mvc.perform(delete("/versions/" + v2).param("version", "" + rowVersion).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()));
		assertThat(file.get("currentVersionId").asLong()).isEqualTo(v1);
		assertThat(file.get("version").asInt()).isGreaterThan(rowVersion);
		assertGone(v2Key);
		rowVersion = file.get("version").asInt();

		// The last version can't be deleted on its own
		mvc.perform(delete("/versions/" + v1).param("version", "" + rowVersion).header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ONLY_VERSION"));

		// Remove the whole file: rows and stored bytes
		String v1Key = versions.findById(v1).orElseThrow().getStoredFileName();
		mvc.perform(delete("/attachments/" + attachmentId).param("version", "" + rowVersion).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		assertGone(v1Key);
		assertThat(versions.findById(v1)).isEmpty();
		mvc.perform(get("/tasks/" + taskId + "/attachments").header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void deletingTheCurrentVersionPromotesTheNewestRemaining() throws Exception {
		User me = newUser("files_promote");
		String token = jwtService.issue(me);
		JsonNode file = body(upload(token, newTask(me), pdf("a.pdf", PDF_V1)));
		long id = file.get("id").asLong();
		file = body(uploadVersion(token, id, pdf("a.pdf", PDF_V2), null, file.get("version").asInt()));
		long v2 = file.get("currentVersionId").asLong();
		file = body(uploadVersion(token, id, pdf("a.pdf", PDF_V1), null, file.get("version").asInt()));
		long v3 = file.get("currentVersionId").asLong();

		file = body(mvc.perform(delete("/versions/" + v3).param("version", file.get("version").asText()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()));
		assertThat(file.get("currentVersionId").asLong()).isEqualTo(v2);
		assertThat(file.get("currentVersionNumber").asInt()).isEqualTo(2);
	}

	@Test
	void uploadsAreCheckedByContent() throws Exception {
		User me = newUser("files_types");
		String token = jwtService.issue(me);
		long taskId = newTask(me);

		upload(token, taskId, new MockMultipartFile("file", "setup.exe", null, "MZ".getBytes()))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("FILE_TYPE"));
		upload(token, taskId, new MockMultipartFile("file", "photo.pdf", null, PNG))
				.andExpect(status().isUnsupportedMediaType());
		upload(token, taskId, new MockMultipartFile("file", "empty.pdf", null, new byte[0]))
				.andExpect(status().isBadRequest());
		upload(token, taskId, new MockMultipartFile("file", "report.docx", null, docx()))
				.andExpect(status().isCreated());
		mvc.perform(multipart("/tasks/" + taskId + "/attachments").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.file").value("Choose a file."));
	}

	@Test
	void anotherUserCannotTouchAnyOfIt() throws Exception {
		User owner = newUser("files_private");
		String ownerToken = jwtService.issue(owner);
		long taskId = newTask(owner);
		JsonNode file = body(upload(ownerToken, taskId, pdf("secret.pdf", PDF_V1)));
		long attachmentId = file.get("id").asLong();
		long versionId = file.get("currentVersionId").asLong();
		int rowVersion = file.get("version").asInt();

		String intruder = jwtService.issue(newUser("files_intruder"));
		expectNotAvailable(mvc.perform(get("/tasks/" + taskId + "/attachments").header("Authorization", "Bearer " + intruder)));
		expectNotAvailable(upload(intruder, taskId, pdf("x.pdf", PDF_V1)));
		expectNotAvailable(mvc.perform(get("/attachments/" + attachmentId + "/versions").header("Authorization", "Bearer " + intruder)));
		expectNotAvailable(uploadVersion(intruder, attachmentId, pdf("x.pdf", PDF_V1), null, rowVersion));
		expectNotAvailable(mvc.perform(get("/versions/" + versionId + "/download").header("Authorization", "Bearer " + intruder)));
		expectNotAvailable(sendVersion(post("/versions/" + versionId + "/restore"), intruder, rowVersion));
		expectNotAvailable(mvc.perform(delete("/versions/" + versionId).param("version", "" + rowVersion).header("Authorization", "Bearer " + intruder)));
		expectNotAvailable(mvc.perform(delete("/attachments/" + attachmentId).param("version", "" + rowVersion).header("Authorization", "Bearer " + intruder)));
	}

	@Test
	void deletingATaskDeletesItsStoredFiles() throws Exception {
		User me = newUser("files_task_delete");
		String token = jwtService.issue(me);
		long taskId = newTask(me);
		JsonNode file = body(upload(token, taskId, pdf("a.pdf", PDF_V1)));
		file = body(uploadVersion(token, file.get("id").asLong(), pdf("a.pdf", PDF_V2), null, file.get("version").asInt()));
		List<String> keys = versions.storedFileNamesForTask(taskId);
		assertThat(keys).hasSize(2);

		mvc.perform(delete("/tasks/" + taskId).param("version", "0").header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
		keys.forEach(this::assertGone);
	}

	private void assertGone(String key) {
		assertThatThrownBy(() -> storage.get(key).content().close()).isInstanceOf(StorageException.class);
	}

	private void expectNotAvailable(ResultActions result) throws Exception {
		result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_AVAILABLE"));
	}

	private User newUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setFullName("File Tester");
		user.setPasswordHash("not-used");
		user = users.save(user);
		createdUsers.add(user.getId());
		return user;
	}

	private long newTask(User user) {
		Task task = new Task();
		task.setUser(user);
		task.setTitle("DBMS assignment");
		task.setCategory(Category.ASSIGNMENT);
		task.setEstimatedHours(BigDecimal.ONE);
		task.setDeadline(Instant.now().plus(Duration.ofDays(3)));
		task.setInitialPriority(Priority.LOW);
		task.setCurrentPriority(Priority.LOW);
		task.setStatus(TaskStatus.PENDING);
		return tasks.save(task).getId();
	}

	private static byte[] docx() throws java.io.IOException {
		var out = new java.io.ByteArrayOutputStream();
		try (var zip = new java.util.zip.ZipOutputStream(out)) {
			zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
			zip.write("<w/>".getBytes());
			zip.closeEntry();
		}
		return out.toByteArray();
	}

	private static MockMultipartFile pdf(String name, byte[] bytes) {
		return new MockMultipartFile("file", name, "application/pdf", bytes);
	}

	private ResultActions upload(String token, long taskId, MockMultipartFile file) throws Exception {
		return mvc.perform(multipart("/tasks/" + taskId + "/attachments").file(file).header("Authorization", "Bearer " + token));
	}

	private ResultActions uploadVersion(String token, long attachmentId, MockMultipartFile file, String note, int version) throws Exception {
		var request = multipart("/attachments/" + attachmentId + "/versions").file(file)
				.param("version", "" + version).header("Authorization", "Bearer " + token);
		if (note != null) {
			request.param("changeDescription", note);
		}
		return mvc.perform(request);
	}

	private ResultActions sendVersion(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String token, int version) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

}
