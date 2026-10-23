package com.justdoit.backend.storage;

import java.io.InputStream;

// Where file bytes live (SRS 16). Postgres holds only metadata; this holds the binaries.
// Picked by storage.provider: "local" (a folder on disk, dev and tests) or "s3" (Backblaze B2).
public interface FileStorage {

	// Keys are always server-generated (StorageKeys), never taken from the client
	void put(String key, InputStream content, long size);

	StoredFile get(String key);

	// Idempotent: deleting a missing key is not an error
	void delete(String key);

	// Caller must close the stream
	record StoredFile(InputStream content, long size) {
	}

}
