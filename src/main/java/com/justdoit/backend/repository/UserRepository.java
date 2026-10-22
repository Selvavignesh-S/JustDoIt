package com.justdoit.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.justdoit.backend.entity.User;

// Callers pass lowercased usernames/emails; the V2 CHECK constraints guarantee stored values are lowercase
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	Optional<User> findByEmailAndEmailVerifiedTrue(String email);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

}
