package com.justdoit.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.justdoit.backend.entity.AttachmentVersion;

public interface AttachmentVersionRepository extends JpaRepository<AttachmentVersion, Long> {

	// Ownership traced Version -> Attachment -> Task -> User: a guessed version id gets nowhere (SRS 17)
	Optional<AttachmentVersion> findByIdAndAttachmentTaskUserId(Long id, Long userId);

	List<AttachmentVersion> findByAttachmentIdOrderByVersionNumberDesc(Long attachmentId);

	long countByAttachmentId(Long attachmentId);

	@Query("select coalesce(max(v.versionNumber), 0) from AttachmentVersion v where v.attachment.id = :attachmentId")
	int maxVersionNumber(Long attachmentId);

	// Replacement when the current version is deleted: the newest one left
	Optional<AttachmentVersion> findFirstByAttachmentIdAndIdNotOrderByVersionNumberDesc(Long attachmentId, Long excludedId);

	// Storage keys to delete after the rows are gone
	@Query("select v.storedFileName from AttachmentVersion v where v.attachment.id = :attachmentId")
	List<String> storedFileNamesForAttachment(Long attachmentId);

	@Query("select v.storedFileName from AttachmentVersion v where v.attachment.task.id = :taskId")
	List<String> storedFileNamesForTask(Long taskId);

}
