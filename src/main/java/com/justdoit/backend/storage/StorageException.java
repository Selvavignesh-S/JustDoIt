package com.justdoit.backend.storage;

// The storage backend couldn't be reached or refused the operation; nothing was changed in the database
public class StorageException extends RuntimeException {

	public StorageException(String message, Throwable cause) {
		super(message, cause);
	}

}
