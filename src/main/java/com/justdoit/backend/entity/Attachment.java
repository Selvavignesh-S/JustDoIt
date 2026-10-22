package com.justdoit.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "attachments")
public class Attachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "attachment_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false, updatable = false)
	private Task task;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	// Nullable on purpose: breaks the circular FK with attachment_versions (SRS 18.7).
	// Insert the attachment with this null, insert its first version, then point this at it.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_version_id")
	private AttachmentVersion currentVersion;

	// Optimistic locking for multi-device edits (SRS 19)
	@Version
	@Column(nullable = false)
	private Integer version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public Task getTask() {
		return task;
	}

	public void setTask(Task task) {
		this.task = task;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public AttachmentVersion getCurrentVersion() {
		return currentVersion;
	}

	public void setCurrentVersion(AttachmentVersion currentVersion) {
		this.currentVersion = currentVersion;
	}

	public Integer getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
