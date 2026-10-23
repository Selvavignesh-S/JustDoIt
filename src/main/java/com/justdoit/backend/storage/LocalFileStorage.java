package com.justdoit.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Development and tests: files under storage.local.base-dir (./storage by default, gitignored)
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

	private final Path root;

	public LocalFileStorage(@Value("${storage.local.base-dir}") String baseDir) {
		this.root = Path.of(baseDir).toAbsolutePath().normalize();
	}

	@Override
	public void put(String key, InputStream content, long size) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			// Write to a temp file first so a half-written upload never appears under the real key
			Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			try {
				Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			finally {
				Files.deleteIfExists(temp);
			}
		}
		catch (IOException e) {
			throw new StorageException("Could not store " + key, e);
		}
	}

	@Override
	public StoredFile get(String key) {
		Path file = resolve(key);
		try {
			return new StoredFile(Files.newInputStream(file), Files.size(file));
		}
		catch (IOException e) {
			throw new StorageException("Could not read " + key, e);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException e) {
			throw new StorageException("Could not delete " + key, e);
		}
	}

	// Keys are server-generated, but refuse anything that would escape the root anyway
	private Path resolve(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Storage key escapes the storage root: " + key);
		}
		return path;
	}

}
