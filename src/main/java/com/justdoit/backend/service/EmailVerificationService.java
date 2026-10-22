package com.justdoit.backend.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.justdoit.backend.entity.EmailVerificationToken;
import com.justdoit.backend.entity.User;
import com.justdoit.backend.exception.ApiException;
import com.justdoit.backend.mail.EmailService;
import com.justdoit.backend.repository.EmailVerificationTokenRepository;
import com.justdoit.backend.util.OneTimeCodes;

// SRS 7.5 step 1: prove the user owns their email before it can be used for password reset
@Service
public class EmailVerificationService {

	private static final Duration TTL = Duration.ofHours(24);

	private final EmailVerificationTokenRepository tokens;
	private final EmailService email;

	public EmailVerificationService(EmailVerificationTokenRepository tokens, EmailService email) {
		this.tokens = tokens;
		this.email = email;
	}

	// Sends a fresh code to user.getEmail(); any earlier unused code stops working
	@Transactional
	public void sendCode(User user) {
		Instant now = Instant.now();
		tokens.markAllUsed(user, now);

		String code = OneTimeCodes.generate();
		EmailVerificationToken token = new EmailVerificationToken();
		token.setUser(user);
		token.setEmail(user.getEmail());
		token.setTokenHash(OneTimeCodes.hash(code));
		token.setExpiresAt(now.plus(TTL));
		tokens.save(token);

		email.send(user.getEmail(), "Confirm your email for JustDoIt", """
				Confirm this email to turn on password reset for your JustDoIt account.

				Open on your phone: justdoit://confirm-email?code=%s

				Or enter this code in the app: %s

				The code works once and expires in 24 hours. If you didn't add this email, ignore this message.
				""".formatted(code, OneTimeCodes.group(code)));
	}

	@Transactional
	public void verify(String code) {
		Instant now = Instant.now();
		EmailVerificationToken token = tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(OneTimeCodes.hash(code), now)
				.orElseThrow(ApiException::invalidToken);
		User user = token.getUser();
		// The code confirms one specific address; it means nothing once the user has switched to another
		if (!token.getEmail().equals(user.getEmail())) {
			throw ApiException.invalidToken();
		}
		token.setUsedAt(now);
		user.setEmailVerified(true);
	}

}
