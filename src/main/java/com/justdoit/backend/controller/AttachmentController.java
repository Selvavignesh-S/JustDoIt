package com.justdoit.backend.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.justdoit.backend.dto.AttachmentResponse;
import com.justdoit.backend.dto.VersionListResponse;
import com.justdoit.backend.dto.VersionRequest;
import com.justdoit.backend.service.AttachmentService;
import com.justdoit.backend.service.JwtService;

import jakarta.validation.Valid;

// Route plan sections 5 and 6. "version" is always the ATTACHMENT's row version (optimistic locking).
@RestController
public class AttachmentController {

	private final AttachmentService attachments;

	public AttachmentController(AttachmentService attachments) {
		this.attachments = attachments;
	}

	@GetMapping("/tasks/{taskId}/attachments")
	List<AttachmentResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable long taskId) {
		return attachments.list(JwtService.userId(jwt), taskId);
	}

	@PostMapping(path = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	AttachmentResponse upload(@AuthenticationPrincipal Jwt jwt, @PathVariable long taskId, @RequestPart("file") MultipartFile file) {
		return attachments.upload(JwtService.userId(jwt), taskId, file);
	}

	@DeleteMapping("/attachments/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteAttachment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam int version) {
		attachments.deleteAttachment(JwtService.userId(jwt), id, version);
	}

	@GetMapping("/attachments/{id}/versions")
	VersionListResponse versions(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return attachments.listVersions(JwtService.userId(jwt), id);
	}

	@PostMapping(path = "/attachments/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	AttachmentResponse uploadVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String changeDescription,
			@RequestParam int version) {
		return attachments.uploadVersion(JwtService.userId(jwt), id, file, changeDescription, version);
	}

	// Streamed through the server, so every byte passes the ownership check and no shareable link exists
	@GetMapping("/versions/{id}/download")
	ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		AttachmentService.Download d = attachments.download(JwtService.userId(jwt), id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(d.contentType()))
				.contentLength(d.file().size())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.body(new InputStreamResource(d.file().content()));
	}

	@PostMapping("/versions/{id}/restore")
	AttachmentResponse restore(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody VersionRequest req) {
		return attachments.restore(JwtService.userId(jwt), id, req.version());
	}

	@DeleteMapping("/versions/{id}")
	AttachmentResponse deleteVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam int version) {
		return attachments.deleteVersion(JwtService.userId(jwt), id, version);
	}

}
