package com.justdoit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.justdoit.backend.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
