package com.justdoit.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.justdoit.backend.entity.Attachment;
import com.justdoit.backend.entity.AttachmentVersion;
import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.PasswordResetToken;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.entity.User;

// Runs against the real Postgres schema (not an in-memory DB) so CHECK constraints, defaults and FKs are exercised.
// Each test is rolled back, so the local database is left untouched.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceMappingTests {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private TaskRepository tasks;

	@Autowired
	private AttachmentRepository attachments;

	@Test
	void taskRoundTripsAndBumpsVersionOnUpdate() {
		User user = em.persist(newUser("mapping_test_user"));
		Instant deadline = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

		Task task = new Task();
		task.setUser(user);
		task.setTitle("DBMS assignment 3");
		task.setCategory(Category.ASSIGNMENT);
		task.setDeadline(deadline);
		task.setEstimatedHours(new BigDecimal("2.5"));
		task.setInitialPriority(Priority.MEDIUM);
		task.setCurrentPriority(Priority.MEDIUM);
		task.setStatus(TaskStatus.PENDING);
		Long id = tasks.saveAndFlush(task).getId();
		em.clear();

		Task loaded = tasks.findById(id).orElseThrow();
		assertThat(loaded.getCategory()).isEqualTo(Category.ASSIGNMENT);
		assertThat(loaded.getDeadline()).isEqualTo(deadline);
		assertThat(loaded.getEstimatedHours()).isEqualByComparingTo("2.5");
		assertThat(loaded.getVersion()).isZero();
		assertThat(loaded.getCreatedAt()).isNotNull();

		loaded.setCurrentPriority(Priority.HIGH);
		tasks.saveAndFlush(loaded);
		assertThat(loaded.getVersion()).isEqualTo(1);
	}

	@Test
	void attachmentAndFirstVersionInsertInSrsOrder() {
		User user = em.persist(newUser("mapping_test_files"));
		Task task = new Task();
		task.setUser(user);
		task.setTitle("Lab report");
		task.setCategory(Category.LAB);
		task.setDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
		task.setEstimatedHours(BigDecimal.ONE);
		task.setInitialPriority(Priority.LOW);
		task.setCurrentPriority(Priority.LOW);
		task.setStatus(TaskStatus.PENDING);
		em.persist(task);

		// SRS 18.7: attachment first (no current version), then the version, then point at it
		Attachment attachment = new Attachment();
		attachment.setTask(task);
		attachment.setDisplayName("report.pdf");
		em.persistAndFlush(attachment);

		AttachmentVersion v1 = new AttachmentVersion();
		v1.setAttachment(attachment);
		v1.setVersionNumber(1);
		v1.setOriginalFileName("report.pdf");
		v1.setStoredFileName("mapping-test-" + System.nanoTime() + ".pdf");
		em.persistAndFlush(v1);

		attachment.setCurrentVersion(v1);
		em.flush();
		em.clear();

		Attachment loaded = attachments.findById(attachment.getId()).orElseThrow();
		assertThat(loaded.getCurrentVersion().getId()).isEqualTo(v1.getId());
		assertThat(loaded.getCurrentVersion().getVersionNumber()).isEqualTo(1);
	}

	@Test
	void passwordResetTokenPersists() {
		User user = em.persist(newUser("mapping_test_reset"));
		PasswordResetToken token = new PasswordResetToken();
		token.setUser(user);
		token.setTokenHash("0".repeat(64));
		token.setExpiresAt(Instant.now().plus(20, ChronoUnit.MINUTES));
		em.persistAndFlush(token);
		em.clear();

		PasswordResetToken loaded = em.find(PasswordResetToken.class, token.getId());
		assertThat(loaded.getUsedAt()).isNull();
		assertThat(loaded.getCreatedAt()).isNotNull();
	}

	private static User newUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setFullName("Mapping Test");
		user.setPasswordHash("not-a-real-hash");
		return user;
	}

}
