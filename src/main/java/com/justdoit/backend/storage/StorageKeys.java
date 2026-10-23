package com.justdoit.backend.storage;

import java.util.UUID;

// Object keys are always made here, from ids the server trusts (SRS 16-17).
// Layout: user_{userId}/task_{taskId}/{uuid}_{safe name}. The UUID (not the version id, which doesn't
// exist until the row is saved) makes every key unique, so a stored file is never overwritten.
public final class StorageKeys {

	private static final int MAX_NAME = 100;

	private StorageKeys() {
	}

	public static String newKey(long userId, long taskId, String fileName) {
		return "user_" + userId + "/task_" + taskId + "/" + UUID.randomUUID() + "_" + safe(fileName);
	}

	// What the user called the file, minus any path a browser might send ("C:\fakepath\a.pdf" -> "a.pdf")
	public static String cleanName(String original) {
		if (original == null) {
			return "file";
		}
		String name = original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).strip();
		if (name.isEmpty()) {
			return "file";
		}
		return name.length() > 255 ? name.substring(name.length() - 255) : name; // keep the extension
	}

	// Only letters, digits, dot, dash, underscore in keys; length capped but the extension kept
	static String safe(String fileName) {
		String s = fileName.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_{2,}", "_").replaceAll("^[.]+", "");
		if (s.isEmpty()) {
			s = "file";
		}
		return s.length() > MAX_NAME ? s.substring(s.length() - MAX_NAME) : s;
	}

}
