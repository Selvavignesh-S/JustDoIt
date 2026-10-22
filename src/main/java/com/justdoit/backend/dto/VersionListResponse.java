package com.justdoit.backend.dto;

import java.util.List;

// Newest first (Route plan 6.1). attachmentVersion is the row version to send with restore / delete / upload.
public record VersionListResponse(int attachmentVersion, List<VersionResponse> versions) {
}
