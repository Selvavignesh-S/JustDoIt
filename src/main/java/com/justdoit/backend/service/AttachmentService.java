package com.justdoit.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.justdoit.backend.dto.AttachmentResponse;
import com.justdoit.backend.dto.VersionListResponse;
import com.justdoit.backend.dto.VersionResponse;
import com.justdoit.backend.entity.Attachment;
import com.justdoit.backend.entity.AttachmentVersion;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.exception.ApiException;
import com.justdoit.backend.repository.AttachmentRepository;
import com.justdoit.backend.repository.AttachmentVersionRepository;
import com.justdoit.backend.repository.TaskRepository;
import com.justdoit.backend.storage.FileStorage;
import com.justdoit.backend.storage.FileTypes;
import com.justdoit.backend.storage.StorageCleanup;
import com.justdoit.backend.storage.StorageException;
import com.justdoit.backend.storage.StorageKeys;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

// Files on tasks with immutable version history (SRS 14-17, Route plan 5-6).
// Every route resolves ownership through the database first; ids from the client prove nothing.
@Service
public class AttachmentService {

	private static final int MAX_CHANGE_NOTE = 500; // UI decision D20

	private final TaskRepository tasks;
	private final AttachmentRepository attachments;
	private final AttachmentVersionRepository versions;
	private final FileStorage storage;
	private final StorageCleanup cleanup;
	private final TransactionTemplate tx;
	private final EntityManager em;

	public AttachmentService(TaskRepository tasks, AttachmentRepository attachments, AttachmentVersionRepository versions,
			FileStorage storage, StorageCleanup cleanup, PlatformTransactionManager txManager, EntityManager em) {
		this.tasks = tasks;
		this.attachments = attachments;
		this.versions = versions;
		this.storage = storage;
		this.cleanup = cleanup;
		this.tx = new TransactionTemplate(txManager);
		this.em = em;
	}

	@Transactional(readOnly = true)
	public List<AttachmentResponse> list(long userId, long taskId) {
		ownedTask(userId, taskId);
		return attachments.findForTask(taskId).stream().map(AttachmentResponse::from).toList();
	}

	// Route plan 5.2: a new file = attachment + its version 1. Allowed on completed tasks too (UI decision D19).
	public AttachmentResponse upload(long userId, long taskId, MultipartFile file) {
		String name = StorageKeys.cleanName(file.getOriginalFilename());
		FileTypes.validate(file, name);
		read(() -> ownedTask(userId, taskId)); // fail before uploading anything

		String key = StorageKeys.newKey(userId, taskId, name);
		store(key, file);
		return saveOrForget(key, () -> {
			// SRS 18.7 insert order: attachment without a current version, then the version, then point at it
			Attachment attachment = new Attachment();
			attachment.setTask(ownedTask(userId, taskId));
			attachment.setDisplayName(name);
			attachments.saveAndFlush(attachment);

			AttachmentVersion first = newVersion(attachment, 1, name, key, null);
			attachment.setCurrentVersion(first);
			return AttachmentResponse.from(attachments.saveAndFlush(attachment));
		});
	}

	// Route plan 6.2: never overwrites; adds version n+1 and makes it current
	public AttachmentResponse uploadVersion(long userId, long attachmentId, MultipartFile file, String changeNote, int version) {
		String note = changeNote == null || changeNote.isBlank() ? null : changeNote.strip();
		if (note != null && note.length() > MAX_CHANGE_NOTE) {
			throw ApiException.validation("changeDescription", "Use at most 500 characters.");
		}
		String name = StorageKeys.cleanName(file.getOriginalFilename());
		FileTypes.validate(file, name);
		long taskId = read(() -> {
			Attachment a = ownedAttachment(userId, attachmentId);
			checkVersion(a, version);
			return a.getTask().getId();
		});

		String key = StorageKeys.newKey(userId, taskId, name);
		store(key, file);
		return saveOrForget(key, () -> {
			// Checked again: the upload may have taken a while, and another device may have changed it meanwhile
			Attachment attachment = ownedAttachment(userId, attachmentId);
			checkVersion(attachment, version);
			AttachmentVersion next = newVersion(attachment, versions.maxVersionNumber(attachmentId) + 1, name, key, note);
			attachment.setCurrentVersion(next); // changes the row, so its version goes up
			return AttachmentResponse.from(attachments.saveAndFlush(attachment));
		});
	}

	@Transactional(readOnly = true)
	public VersionListResponse listVersions(long userId, long attachmentId) {
		Attachment attachment = ownedAttachment(userId, attachmentId);
		Long currentId = attachment.getCurrentVersion() == null ? null : attachment.getCurrentVersion().getId();
		return new VersionListResponse(attachment.getVersion(),
				versions.findByAttachmentIdOrderByVersionNumberDesc(attachmentId).stream()
						.map(v -> VersionResponse.from(v, currentId)).toList());
	}

	// Route plan 6.3. The ownership check is the whole point: a guessed version id must not download anything.
	@Transactional(readOnly = true)
	public Download download(long userId, long versionId) {
		AttachmentVersion v = ownedVersion(userId, versionId);
		return new Download(v.getOriginalFileName(), FileTypes.contentType(v.getOriginalFileName()), storage.get(v.getStoredFileName()));
	}

	// Route plan 6.4: only the pointer moves; newer versions stay stored and restorable (SRS 15)
	@Transactional
	public AttachmentResponse restore(long userId, long versionId, int version) {
		AttachmentVersion target = ownedVersion(userId, versionId);
		Attachment attachment = target.getAttachment();
		checkVersion(attachment, version);
		attachment.setCurrentVersion(target);
		return AttachmentResponse.from(attachments.saveAndFlush(attachment));
	}

	// Route plan 6.5: never leaves an attachment with zero versions; deleting the current one promotes the newest left
	@Transactional
	public AttachmentResponse deleteVersion(long userId, long versionId, int version) {
		AttachmentVersion target = ownedVersion(userId, versionId);
		Attachment attachment = target.getAttachment();
		checkVersion(attachment, version);
		if (versions.countByAttachmentId(attachment.getId()) <= 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ONLY_VERSION",
					"The only version can't be deleted. Use Remove file to delete the whole file.", null);
		}
		AttachmentVersion current = attachment.getCurrentVersion();
		if (current != null && current.getId().equals(target.getId())) {
			attachment.setCurrentVersion(versions.findFirstByAttachmentIdAndIdNotOrderByVersionNumberDesc(attachment.getId(), target.getId())
					.orElseThrow());
		}
		else {
			// The row itself doesn't change, but its version list does: bump the version (and lock the row) so a
			// second device deleting "the other one" at the same moment gets a conflict instead of leaving zero versions
			em.lock(attachment, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
		}
		attachments.saveAndFlush(attachment);
		versions.delete(target);
		versions.flush();
		cleanup.deleteAfterCommit(List.of(target.getStoredFileName()));
		return AttachmentResponse.from(attachment);
	}

	// Route plan 5.3: the whole file, every version
	@Transactional
	public void deleteAttachment(long userId, long attachmentId, int version) {
		Attachment attachment = ownedAttachment(userId, attachmentId);
		checkVersion(attachment, version);
		List<String> keys = versions.storedFileNamesForAttachment(attachmentId);
		attachments.delete(attachment); // the DB cascade removes the version rows
		attachments.flush();
		cleanup.deleteAfterCommit(keys);
	}

	public record Download(String fileName, String contentType, FileStorage.StoredFile file) {
	}

	private AttachmentVersion newVersion(Attachment attachment, int number, String name, String key, String note) {
		AttachmentVersion v = new AttachmentVersion();
		v.setAttachment(attachment);
		v.setVersionNumber(number);
		v.setOriginalFileName(name);
		v.setStoredFileName(key);
		v.setChangeDescription(note);
		return versions.saveAndFlush(v);
	}

	private void store(String key, MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			storage.put(key, in, file.getSize());
		}
		catch (IOException e) {
			throw new StorageException("Could not read the upload", e);
		}
	}

	// The file is stored before the rows exist (no DB connection is held during a slow upload).
	// If saving the rows fails for any reason, the stored file is removed again: nothing half-created remains.
	private <T> T saveOrForget(String key, Supplier<T> save) {
		try {
			return tx.execute(status -> save.get());
		}
		catch (RuntimeException e) {
			cleanup.deleteQuietly(key);
			throw e;
		}
	}

	private <T> T read(Supplier<T> query) {
		TransactionTemplate readOnly = new TransactionTemplate(tx.getTransactionManager());
		readOnly.setReadOnly(true);
		return readOnly.execute(status -> query.get());
	}

	private Task ownedTask(long userId, long taskId) {
		return tasks.findByIdAndUserId(taskId, userId).orElseThrow(ApiException::notAvailable);
	}

	private Attachment ownedAttachment(long userId, long attachmentId) {
		return attachments.findByIdAndTaskUserId(attachmentId, userId).orElseThrow(ApiException::notAvailable);
	}

	private AttachmentVersion ownedVersion(long userId, long versionId) {
		return versions.findByIdAndAttachmentTaskUserId(versionId, userId).orElseThrow(ApiException::notAvailable);
	}

	private static void checkVersion(Attachment attachment, int clientVersion) {
		if (attachment.getVersion() != clientVersion) {
			throw ApiException.fileConflict();
		}
	}

}
