package com.justdoit.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.justdoit.backend.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	// Ownership traced Attachment -> Task -> User inside the query (SRS 7.4, 18)
	Optional<Attachment> findByIdAndTaskUserId(Long id, Long userId);

	// Current version fetched in the same query, so listing n files is one SELECT, not n+1
	@Query("select a from Attachment a left join fetch a.currentVersion where a.task.id = :taskId order by a.id")
	List<Attachment> findForTask(Long taskId);

}
