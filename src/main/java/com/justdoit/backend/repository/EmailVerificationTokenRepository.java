package com.justdoit.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.justdoit.backend.entity.EmailVerificationToken;
import com.justdoit.backend.entity.User;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

	Optional<EmailVerificationToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);

	@Modifying
	@Query("update EmailVerificationToken t set t.usedAt = :now where t.user = :user and t.usedAt is null")
	int markAllUsed(User user, Instant now);

}
