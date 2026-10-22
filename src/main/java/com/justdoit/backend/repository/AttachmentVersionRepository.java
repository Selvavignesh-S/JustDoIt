package com.justdoit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.justdoit.backend.entity.AttachmentVersion;

public interface AttachmentVersionRepository extends JpaRepository<AttachmentVersion, Long> {
}
