package com.justdoit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.justdoit.backend.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}
