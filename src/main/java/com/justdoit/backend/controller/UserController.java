package com.justdoit.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.justdoit.backend.dto.EmailRequest;
import com.justdoit.backend.dto.MeResponse;
import com.justdoit.backend.service.JwtService;
import com.justdoit.backend.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me")
public class UserController {

	private final UserService users;

	public UserController(UserService users) {
		this.users = users;
	}

	@GetMapping
	MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return MeResponse.from(users.get(JwtService.userId(jwt)));
	}

	@PatchMapping
	MeResponse updateEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EmailRequest req) {
		return MeResponse.from(users.updateEmail(JwtService.userId(jwt), req.email()));
	}

	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resendVerification(@AuthenticationPrincipal Jwt jwt) {
		users.resendVerification(JwtService.userId(jwt));
	}

}
