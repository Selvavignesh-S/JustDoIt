package com.justdoit.backend.service;

import java.time.Duration;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.justdoit.backend.entity.User;
import com.justdoit.backend.exception.ApiException;
import com.justdoit.backend.repository.AttachmentVersionRepository;
import com.justdoit.backend.repository.UserRepository;
import com.justdoit.backend.storage.StorageCleanup;
import com.justdoit.backend.util.RateLimiter;

// The signed-in user's own account (Route plan 2.7-2.9). The id always comes from the JWT, never from the URL.
@Service
public class UserService {

	// Not in SRS 21.2's list: without it, a signed-in user could make the server email any address over and over
	private static final int VERIFICATION_EMAILS_PER_HOUR = 5;

	private final UserRepository users;
	private final EmailVerificationService verification;
	private final RateLimiter rateLimiter;
	private final PasswordEncoder passwordEncoder;
	private final AttachmentVersionRepository versions;
	private final StorageCleanup storageCleanup;

	public UserService(UserRepository users, EmailVerificationService verification, RateLimiter rateLimiter,
			PasswordEncoder passwordEncoder, AttachmentVersionRepository versions, StorageCleanup storageCleanup) {
		this.users = users;
		this.verification = verification;
		this.rateLimiter = rateLimiter;
		this.passwordEncoder = passwordEncoder;
		this.versions = versions;
		this.storageCleanup = storageCleanup;
	}

	public User get(long userId) {
		// A valid token for a user that no longer exists is treated like an expired token
		return users.findById(userId).orElseThrow(ApiException::unauthorized);
	}

	@Transactional
	public User updateEmail(long userId, String newEmail) {
		User user = get(userId);
		String normalized = AuthService.normalize(newEmail);
		if (normalized.equals(user.getEmail())) {
			return user;
		}
		if (users.existsByEmail(normalized)) {
			throw ApiException.validation("email", "That email is already in use.");
		}
		limitVerificationEmails(user);
		user.setEmail(normalized);
		user.setEmailVerified(false); // a new address always needs its own confirmation
		verification.sendCode(user);
		return user;
	}

	@Transactional
	public boolean emailEnabled() {
		return verification.emailEnabled();
	}

	public void resendVerification(long userId) {
		User user = get(userId);
		if (user.getEmail() == null || user.isEmailVerified()) {
			throw ApiException.validation("email", "There is no unconfirmed email to confirm.");
		}
		if (!verification.emailEnabled()) {
			throw ApiException.emailDisabled();
		}
		limitVerificationEmails(user);
		verification.sendCode(user);
	}

	// Google Play's account deletion. The DB cascade (V1-V3) removes tasks, attachments, versions, reset and
	// verification codes, push tokens and reminder records; stored files go once the delete has committed.
	@Transactional
	public void deleteAccount(long userId, String password) {
		User user = get(userId);
		if (AuthService.tooLongForBcrypt(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw ApiException.badCredentials();
		}
		List<String> storedFiles = versions.storedFileNamesForUser(userId);
		users.delete(user);
		users.flush();
		storageCleanup.deleteAfterCommit(storedFiles);
	}

	private void limitVerificationEmails(User user) {
		if (!rateLimiter.tryAcquire("verify-email:user:" + user.getId(), VERIFICATION_EMAILS_PER_HOUR, Duration.ofHours(1))) {
			throw ApiException.rateLimited();
		}
	}

}
