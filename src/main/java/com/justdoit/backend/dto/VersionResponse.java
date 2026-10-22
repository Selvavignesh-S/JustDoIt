package com.justdoit.backend.dto;

import java.time.Instant;

import com.justdoit.backend.entity.AttachmentVersion;

public record VersionResponse(long id, int versionNumber, Instant uploadedAt, String originalFileName,
		String changeDescription, boolean current) {

	public static VersionResponse from(AttachmentVersion v, Long currentVersionId) {
		return new VersionResponse(v.getId(), v.getVersionNumber(), v.getUploadedAt(), v.getOriginalFileName(),
				v.getChangeDescription(), v.getId().equals(currentVersionId));
	}

}
