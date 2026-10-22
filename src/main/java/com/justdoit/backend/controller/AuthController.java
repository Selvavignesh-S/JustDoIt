package com.justdoit.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.justdoit.backend.dto.CodeRequest;
import com.justdoit.backend.dto.EmailRequest;
import com.justdoit.backend.dto.LoginRequest;
import com.justdoit.backend.dto.MeResponse;
import com.justdoit.backend.dto.MessageResponse;
import com.justdoit.backend.dto.RegisterRequest;
import com.justdoit.backend.dto.ResetConfirmRequest;
import com.justdoit.backend.dto.TokenResponse;
import com.justdoit.backend.service.AuthService;
import com.justdoit.backend.service.EmailVerificationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

// Public routes (Route plan 2.1-2.6): no JWT needed, see SecurityConfig
@RestController
@RequestMapping("/auth")
public class AuthController {

	private static final MessageResponse RESET_REQUESTED = new MessageResponse(
			"If an account with that email exists, a reset link has been sent.");

	private final AuthService auth;
	private final EmailVerificationService verification;

	public AuthController(AuthService auth, EmailVerificationService verification) {
		this.auth = auth;
		this.verification = verification;
	}

	// No token here on purpose: the app calls /auth/login next, so only one place issues tokens
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	MeResponse register(@Valid @RequestBody RegisterRequest req) {
		return MeResponse.from(auth.register(req));
	}

	@PostMapping("/login")
	TokenResponse login(@Valid @RequestBody LoginRequest req) {
		return new TokenResponse(auth.login(req));
	}

	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void verifyEmail(@Valid @RequestBody CodeRequest req) {
		verification.verify(req.code());
	}

	@PostMapping("/password-reset/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	MessageResponse requestReset(@Valid @RequestBody EmailRequest req, HttpServletRequest http) {
		auth.requestPasswordReset(req.email(), http.getRemoteAddr());
		return RESET_REQUESTED;
	}

	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirmReset(@Valid @RequestBody ResetConfirmRequest req) {
		auth.confirmPasswordReset(req.code(), req.newPassword());
	}

}
