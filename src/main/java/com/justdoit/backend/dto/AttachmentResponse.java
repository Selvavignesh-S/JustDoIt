package com.justdoit.backend.dto;

import java.time.Instant;

import com.justdoit.backend.entity.Attachment;

// One file on a task (Route plan 5.1). Carries what the file menu needs to act without opening the
// version list: the current version's id (download) and the attachment's row version (remove, restore...).
public record AttachmentResponse(long id, String displayName, Long currentVersionId, Integer currentVersionNumber,
		Instant updatedAt, int version) {

	public static AttachmentResponse from(Attachment a) {
		var current = a.getCurrentVersion();
		return new AttachmentResponse(a.getId(), a.getDisplayName(), current == null ? null : current.getId(),
				current == null ? null : current.getVersionNumber(), a.getUpdatedAt(), a.getVersion());
	}

}
