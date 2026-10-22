package com.justdoit.backend.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.justdoit.backend.dto.ReminderResponse;
import com.justdoit.backend.service.JwtService;
import com.justdoit.backend.service.ReminderService;

// Route plan 4.1. No "mark as read" route on purpose: reminders aren't stored (SRS 13).
@RestController
@RequestMapping("/reminders")
public class ReminderController {

	private final ReminderService reminders;

	public ReminderController(ReminderService reminders) {
		this.reminders = reminders;
	}

	@GetMapping
	List<ReminderResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return reminders.forUser(JwtService.userId(jwt), Instant.now());
	}

}
