package com.justdoit.backend.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.justdoit.backend.entity.Priority;
import com.justdoit.backend.entity.Task;
import com.justdoit.backend.entity.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	// Ownership is part of the lookup: another user's task is simply "not found" (SRS 7.4)
	Optional<Task> findByIdAndUserId(Long id, Long userId);

	// Scheduler: walks every pending task in id order, one chunk at a time
	Slice<Task> findByStatus(TaskStatus status, Pageable pageable);

	// A bulk UPDATE skips @Version on purpose: priority drifting with time is not a user edit, so it must not
	// make the user's next save fail with "changed on another device". The status check skips any task
	// completed since it was read.
	@Modifying
	@Query("update Task t set t.currentPriority = :priority where t.id in :ids and t.status = :status")
	int updateCurrentPriority(Collection<Long> ids, Priority priority, TaskStatus status);

}
