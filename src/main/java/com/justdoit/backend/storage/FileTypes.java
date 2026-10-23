package com.justdoit.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import com.justdoit.backend.exception.ApiException;

// SRS 17: max 50 MB; PDF, DOCX, PPTX, XLSX, TXT, PNG, JPG/JPEG, ZIP only. The server checks the bytes,
// not just the name or the client's Content-Type, because a file called "notes.pdf" can be anything.
public final class FileTypes {

	public static final long MAX_BYTES = 50L * 1024 * 1024;

	private static final Map<String, String> CONTENT_TYPES = Map.of(
			"pdf", "application/pdf",
			"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"txt", "text/plain",
			"png", "image/png",
			"jpg", "image/jpeg",
			"jpeg", "image/jpeg",
			"zip", "application/zip");

	// Office files are ZIP archives; this entry is what makes each one a real document of its kind
	private static final Map<String, String> OFFICE_MARKER = Map.of(
			"docx", "word/document.xml",
			"pptx", "ppt/presentation.xml",
			"xlsx", "xl/workbook.xml");

	private static final byte[] PDF = "%PDF-".getBytes();
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] ZIP = { 'P', 'K', 3, 4 };
	private static final byte[] EMPTY_ZIP = { 'P', 'K', 5, 6 };

	private FileTypes() {
	}

	// Throws ApiException (413/415/400) unless the upload is an allowed, non-empty file whose content matches its extension
	public static void validate(MultipartFile file, String cleanName) {
		if (file.getSize() > MAX_BYTES) {
			throw tooLarge();
		}
		if (file.isEmpty()) {
			throw ApiException.validation("file", "This file is empty.");
		}
		String ext = extension(cleanName);
		if (!CONTENT_TYPES.containsKey(ext) || !contentMatches(file, ext)) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE",
					"This file type isn't supported. Use PDF, DOCX, PPTX, XLSX, TXT, PNG, JPG or ZIP.", null);
		}
	}

	public static ApiException tooLarge() {
		return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "This file is over 50 MB. Choose a smaller file.", null);
	}

	// For downloads: decided by the stored extension, never by what the uploader claimed
	public static String contentType(String fileName) {
		return CONTENT_TYPES.getOrDefault(extension(fileName), "application/octet-stream");
	}

	static String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean contentMatches(MultipartFile file, String ext) {
		byte[] head = head(file, 8192);
		return switch (ext) {
			case "pdf" -> startsWith(head, PDF);
			case "png" -> startsWith(head, PNG);
			case "jpg", "jpeg" -> startsWith(head, JPEG);
			case "zip" -> startsWith(head, ZIP) || startsWith(head, EMPTY_ZIP);
			case "docx", "pptx", "xlsx" -> startsWith(head, ZIP) && zipContains(file, OFFICE_MARKER.get(ext));
			// Plain text never contains a NUL byte; binaries almost always do in their first few KB
			case "txt" -> indexOfZero(head) < 0;
			default -> false;
		};
	}

	private static byte[] head(MultipartFile file, int max) {
		try (InputStream in = file.getInputStream()) {
			return in.readNBytes(max);
		}
		catch (IOException e) {
			throw new StorageException("Could not read the upload", e);
		}
	}

	// Reads only the archive's table of contents (ZipFile), never inflates entries, so a zip bomb costs nothing
	private static boolean zipContains(MultipartFile file, String entry) {
		Path temp = null;
		try {
			temp = Files.createTempFile("justdoit-upload-", ".zip");
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
			}
			try (ZipFile zip = new ZipFile(temp.toFile())) {
				return zip.getEntry(entry) != null;
			}
		}
		catch (IOException e) {
			return false; // not a readable ZIP, so not a real Office file
		}
		finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				}
				catch (IOException ignored) {
					// temp dir is cleaned by the OS
				}
			}
		}
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
	}

	private static int indexOfZero(byte[] data) {
		for (int i = 0; i < data.length; i++) {
			if (data[i] == 0) {
				return i;
			}
		}
		return -1;
	}

}
