package com.justdoit.backend.controller;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.justdoit.backend.dto.AnalyzeRequest;
import com.justdoit.backend.dto.PageResponse;
import com.justdoit.backend.dto.TaskCreateRequest;
import com.justdoit.backend.dto.TaskResponse;
import com.justdoit.backend.dto.TaskSuggestion;
import com.justdoit.backend.dto.TaskSummaryResponse;
import com.justdoit.backend.dto.TaskUpdateRequest;
import com.justdoit.backend.dto.VersionRequest;
import com.justdoit.backend.entity.Category;
import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskScope;
import com.justdoit.backend.entity.TaskSort;
import com.justdoit.backend.entity.TaskStatus;
import com.justdoit.backend.service.JwtService;
import com.justdoit.backend.service.TaskService;

import jakarta.validation.Valid;

// Route plan 3.1-3.9
@RestController
@RequestMapping("/tasks")
public class TaskController {

	private final TaskService tasks;

	public TaskController(TaskService tasks) {
		this.tasks = tasks;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TaskResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TaskCreateRequest req) {
		Instant now = Instant.now();
		return TaskResponse.from(tasks.create(JwtService.userId(jwt), req, now), now);
	}

	// Route plan 3.1: AI suggestion only, nothing saved. Rate limited; 503 AI_UNAVAILABLE means "use the manual form".
	@PostMapping("/analyze")
	TaskSuggestion analyze(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AnalyzeRequest req) {
		return tasks.analyze(JwtService.userId(jwt), req.description());
	}

	@GetMapping
	PageResponse<TaskResponse> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) TaskStatus status,
			@RequestParam(required = false) Priority priority,
			@RequestParam(required = false) Category category,
			@RequestParam(required = false) TaskScope scope,
			@RequestParam(defaultValue = "PRIORITY") TaskSort sort,
			@RequestParam(defaultValue = "0") int page) {
		Instant now = Instant.now();
		Page<Task> result = tasks.list(JwtService.userId(jwt), status, priority, category, scope, sort, page, now);
		return new PageResponse<>(result.map(t -> TaskResponse.from(t, now)).getContent(), result.getNumber(), result.hasNext());
	}

	@GetMapping("/summary")
	TaskSummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
		return tasks.summary(JwtService.userId(jwt), Instant.now());
	}

	@GetMapping("/{id}")
	TaskResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return TaskResponse.from(tasks.get(JwtService.userId(jwt), id), Instant.now());
	}

	@PatchMapping("/{id}")
	TaskResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody TaskUpdateRequest req) {
		Instant now = Instant.now();
		return TaskResponse.from(tasks.update(JwtService.userId(jwt), id, req, now), now);
	}

	// Returns the new row version, which the app's "Undo" needs to call reopen (Route plan 3.7)
	@PostMapping("/{id}/complete")
	TaskResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody VersionRequest req) {
		return TaskResponse.from(tasks.complete(JwtService.userId(jwt), id, req.version()), Instant.now());
	}

	@PostMapping("/{id}/reopen")
	TaskResponse reopen(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody VersionRequest req) {
		Instant now = Instant.now();
		return TaskResponse.from(tasks.reopen(JwtService.userId(jwt), id, req.version(), now), now);
	}

	// Version as a query parameter: some HTTP clients drop the body on DELETE (Route plan 3.9)
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam int version) {
		tasks.delete(JwtService.userId(jwt), id, version);
	}

}
