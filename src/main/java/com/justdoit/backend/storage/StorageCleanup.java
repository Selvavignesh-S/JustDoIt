package com.justdoit.backend.storage;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Keeps the database and storage in step. Files are deleted only once the database change has really
// committed: deleting first and then rolling back would leave rows pointing at missing files.
@Component
public class StorageCleanup {

	private static final Logger log = LoggerFactory.getLogger(StorageCleanup.class);

	private final FileStorage storage;

	public StorageCleanup(FileStorage storage) {
		this.storage = storage;
	}

	public void deleteAfterCommit(Collection<String> keys) {
		List<String> copy = List.copyOf(keys);
		if (copy.isEmpty()) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			copy.forEach(this::deleteQuietly);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				copy.forEach(StorageCleanup.this::deleteQuietly);
			}
		});
	}

	// A failed delete leaves an orphaned file (wasted space, never visible to anyone): log it, don't fail the request
	public void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (RuntimeException e) {
			log.warn("Orphaned stored file, delete failed: {}", key, e);
		}
	}

}
