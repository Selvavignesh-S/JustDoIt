package com.justdoit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.justdoit.backend.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
}
