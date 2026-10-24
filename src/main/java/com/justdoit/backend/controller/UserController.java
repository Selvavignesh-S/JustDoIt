package com.justdoit.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.justdoit.backend.dto.DeleteAccountRequest;
import com.justdoit.backend.dto.EmailRequest;
import com.justdoit.backend.dto.MeResponse;
import com.justdoit.backend.dto.PushTokenRequest;
import com.justdoit.backend.push.PushService;
import com.justdoit.backend.service.JwtService;
import com.justdoit.backend.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me")
public class UserController {

	private final UserService users;
	private final PushService push;

	public UserController(UserService users, PushService push) {
		this.users = users;
		this.push = push;
	}

	@GetMapping
	MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return MeResponse.from(users.get(JwtService.userId(jwt)), users.emailEnabled());
	}

	@PatchMapping
	MeResponse updateEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EmailRequest req) {
		return MeResponse.from(users.updateEmail(JwtService.userId(jwt), req.email()), users.emailEnabled());
	}

	// Deletes the account and everything in it, for good
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest req) {
		users.deleteAccount(JwtService.userId(jwt), req.password());
	}

	@PostMapping("/resend-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resendVerification(@AuthenticationPrincipal Jwt jwt) {
		users.resendVerification(JwtService.userId(jwt));
	}

	// This phone wants reminder notifications (sent after every sign-in; repeating it is harmless)
	@PutMapping("/push-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void registerPushToken(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PushTokenRequest req) {
		push.register(JwtService.userId(jwt), req.token());
	}

	// Log out: this phone stops getting this account's reminders
	@DeleteMapping("/push-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removePushToken(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PushTokenRequest req) {
		push.unregister(JwtService.userId(jwt), req.token());
	}

}
