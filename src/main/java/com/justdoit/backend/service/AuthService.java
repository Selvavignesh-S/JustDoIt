package com.justdoit.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.justdoit.backend.dto.LoginRequest;
import com.justdoit.backend.dto.RegisterRequest;
import com.justdoit.backend.entity.PasswordResetToken;
import com.justdoit.backend.entity.User;
import com.justdoit.backend.exception.ApiException;
import com.justdoit.backend.mail.EmailService;
import com.justdoit.backend.repository.PasswordResetTokenRepository;
import com.justdoit.backend.repository.UserRepository;
import com.justdoit.backend.util.OneTimeCodes;
import com.justdoit.backend.util.RateLimiter;

@Service
public class AuthService {

	private static final Duration RESET_TTL = Duration.ofMinutes(30); // SRS 7.5: 15-30 minutes
	private static final Duration RESET_WINDOW = Duration.ofHours(1);
	private static final int RESETS_PER_EMAIL = 3;
	private static final int RESETS_PER_IP = 10;
	private static final int BCRYPT_MAX_BYTES = 72; // BCrypt ignores (newer Spring rejects) anything longer

	private final UserRepository users;
	private final PasswordResetTokenRepository resetTokens;
	private final EmailVerificationService verification;
	private final EmailService email;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwt;
	private final RateLimiter rateLimiter;
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordResetTokenRepository resetTokens, EmailVerificationService verification,
			EmailService email, PasswordEncoder passwordEncoder, JwtService jwt, RateLimiter rateLimiter) {
		this.users = users;
		this.resetTokens = resetTokens;
		this.verification = verification;
		this.email = email;
		this.passwordEncoder = passwordEncoder;
		this.jwt = jwt;
		this.rateLimiter = rateLimiter;
		// Login checks a password against this when the username doesn't exist, so both cases take the same ~100 ms
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	// "Selva" and "selva" are the same account (V2 migration enforces lowercase in the DB)
	public static String normalize(String identity) {
		return identity.trim().toLowerCase(Locale.ROOT);
	}

	@Transactional
	public User register(RegisterRequest req) {
		String username = normalize(req.username());
		String userEmail = req.email() == null || req.email().isBlank() ? null : normalize(req.email());

		Map<String, String> problems = new LinkedHashMap<>();
		if (users.existsByUsername(username)) {
			problems.put("username", "That username is taken.");
		}
		if (userEmail != null && users.existsByEmail(userEmail)) {
			problems.put("email", "That email is already in use.");
		}
		if (tooLongForBcrypt(req.password())) {
			problems.put("password", "That password is too long.");
		}
		if (!problems.isEmpty()) {
			throw ApiException.validation(problems);
		}

		User user = new User();
		user.setUsername(username);
		user.setFullName(req.fullName().trim());
		user.setEmail(userEmail);
		user.setPasswordHash(passwordEncoder.encode(req.password()));
		// shortcut: two simultaneous sign-ups with the same name both pass the check above; the DB UNIQUE stops the second,
		// which then gets a generic 500 instead of "taken". Rare enough to leave for V1.
		users.save(user);

		if (userEmail != null) {
			verification.sendCode(user);
		}
		return user;
	}

	public String login(LoginRequest req) {
		Optional<User> user = users.findByUsername(normalize(req.username()));
		if (tooLongForBcrypt(req.password())) {
			throw ApiException.badCredentials();
		}
		boolean passwordMatches = passwordEncoder.matches(req.password(), user.map(User::getPasswordHash).orElse(dummyHash));
		if (user.isEmpty() || !passwordMatches) {
			throw ApiException.badCredentials();
		}
		return jwt.issue(user.get());
	}

	// SRS 7.5 steps 2-3. Callers always return the same response, whether or not anything was sent.
	@Transactional
	public void requestPasswordReset(String rawEmail, String clientIp) {
		if (email.isDisabled()) {
			throw ApiException.emailDisabled();
		}
		String resetEmail = normalize(rawEmail);
		if (!rateLimiter.tryAcquire("reset:email:" + resetEmail, RESETS_PER_EMAIL, RESET_WINDOW)
				|| !rateLimiter.tryAcquire("reset:ip:" + clientIp, RESETS_PER_IP, RESET_WINDOW)) {
			throw ApiException.rateLimited();
		}

		users.findByEmailAndEmailVerifiedTrue(resetEmail).ifPresent(user -> {
			String code = OneTimeCodes.generate();
			PasswordResetToken token = new PasswordResetToken();
			token.setUser(user);
			token.setTokenHash(OneTimeCodes.hash(code));
			token.setExpiresAt(Instant.now().plus(RESET_TTL));
			resetTokens.save(token);

			email.send(resetEmail, "Reset your JustDoIt password", """
					Someone asked to reset the password for your JustDoIt account (%s).

					Open on your phone: justdoit://reset?code=%s

					Or enter this code in the app: %s

					The code works once and expires in 30 minutes. If this wasn't you, ignore this message; your password stays the same.
					""".formatted(user.getUsername(), code, OneTimeCodes.group(code)));
		});
	}

	// SRS 7.5 steps 4-5
	@Transactional
	public void confirmPasswordReset(String code, String newPassword) {
		if (tooLongForBcrypt(newPassword)) {
			throw ApiException.validation("newPassword", "That password is too long.");
		}
		Instant now = Instant.now();
		PasswordResetToken token = resetTokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(OneTimeCodes.hash(code), now)
				.orElseThrow(ApiException::invalidToken);
		User user = token.getUser();
		// Consume this code and every other outstanding one before the password changes
		resetTokens.markAllUsed(user, now);
		user.setPasswordHash(passwordEncoder.encode(newPassword));
	}

	private static boolean tooLongForBcrypt(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
	}

}
