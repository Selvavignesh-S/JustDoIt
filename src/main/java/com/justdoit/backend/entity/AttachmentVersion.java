package com.justdoit.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Immutable once uploaded (SRS 15): no setters are used after the first save.
// The table's own `version` column is left unmapped; optimistic locking lives on the parent Attachment.
@Entity
@Table(name = "attachment_versions")
public class AttachmentVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "version_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attachment_id", nullable = false, updatable = false)
	private Attachment attachment;

	@Column(name = "version_number", nullable = false, updatable = false)
	private Integer versionNumber;

	@Column(name = "original_file_name", nullable = false, updatable = false)
	private String originalFileName;

	@Column(name = "stored_file_name", nullable = false, unique = true, updatable = false)
	private String storedFileName;

	@Column(name = "change_description", columnDefinition = "TEXT", updatable = false)
	private String changeDescription;

	@CreationTimestamp
	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	public Long getId() {
		return id;
	}

	public Attachment getAttachment() {
		return attachment;
	}

	public void setAttachment(Attachment attachment) {
		this.attachment = attachment;
	}

	public Integer getVersionNumber() {
		return versionNumber;
	}

	public void setVersionNumber(Integer versionNumber) {
		this.versionNumber = versionNumber;
	}

	public String getOriginalFileName() {
		return originalFileName;
	}

	public void setOriginalFileName(String originalFileName) {
		this.originalFileName = originalFileName;
	}

	public String getStoredFileName() {
		return storedFileName;
	}

	public void setStoredFileName(String storedFileName) {
		this.storedFileName = storedFileName;
	}

	public String getChangeDescription() {
		return changeDescription;
	}

	public void setChangeDescription(String changeDescription) {
		this.changeDescription = changeDescription;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

}
