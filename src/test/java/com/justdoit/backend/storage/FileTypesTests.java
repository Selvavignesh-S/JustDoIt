package com.justdoit.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.justdoit.backend.exception.ApiException;

class FileTypesTests {

	static final byte[] PDF = "%PDF-1.7\nhello".getBytes();
	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };

	static byte[] zipWith(String... entries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (String e : entries) {
				zip.putNextEntry(new ZipEntry(e));
				zip.write("<x/>".getBytes());
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static void ok(String name, byte[] content) {
		assertThatCode(() -> FileTypes.validate(new MockMultipartFile("file", name, null, content), name)).doesNotThrowAnyException();
	}

	private static void rejected(String name, byte[] content, String code) {
		assertThatThrownBy(() -> FileTypes.validate(new MockMultipartFile("file", name, null, content), name))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
	}

	@Test
	void acceptsEachAllowedTypeWhenTheContentMatches() throws IOException {
		ok("notes.pdf", PDF);
		ok("NOTES.PDF", PDF);
		ok("diagram.png", PNG);
		ok("photo.jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10 });
		ok("photo.jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10 });
		ok("readme.txt", "plain text, ünïcode ok".getBytes());
		ok("code.zip", zipWith("a.java"));
		ok("report.docx", zipWith("[Content_Types].xml", "word/document.xml"));
		ok("slides.pptx", zipWith("[Content_Types].xml", "ppt/presentation.xml"));
		ok("sheet.xlsx", zipWith("[Content_Types].xml", "xl/workbook.xml"));
	}

	@Test
	void rejectsWrongTypesAndDisguisedFiles() throws IOException {
		rejected("virus.exe", "MZ...".getBytes(), "FILE_TYPE");
		rejected("noextension", PDF, "FILE_TYPE");
		rejected("image.pdf", PNG, "FILE_TYPE"); // PNG bytes renamed to .pdf
		rejected("fake.docx", zipWith("a.java"), "FILE_TYPE"); // a ZIP, but not a Word document
		rejected("slides.docx", zipWith("ppt/presentation.xml"), "FILE_TYPE"); // a real pptx renamed to .docx
		rejected("binary.txt", new byte[] { 'a', 0, 'b' }, "FILE_TYPE");
		rejected("empty.pdf", new byte[0], "VALIDATION");
	}

	@Test
	void overFiftyMegabytesIsTooLarge() {
		MockMultipartFile huge = new MockMultipartFile("file", "big.pdf", null, PDF) {
			@Override
			public long getSize() {
				return FileTypes.MAX_BYTES + 1;
			}
		};
		assertThatThrownBy(() -> FileTypes.validate(huge, "big.pdf"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("FILE_TOO_LARGE"));
	}

	@Test
	void storageKeysAreSafeAndUnique() {
		assertThat(StorageKeys.cleanName("C:\\fakepath\\My Notes.pdf")).isEqualTo("My Notes.pdf");
		assertThat(StorageKeys.cleanName("../../etc/passwd")).isEqualTo("passwd");
		assertThat(StorageKeys.cleanName(null)).isEqualTo("file");
		assertThat(StorageKeys.safe("Résumé (final) v2.pdf")).isEqualTo("R_sum_final_v2.pdf");
		assertThat(StorageKeys.safe("..hidden")).isEqualTo("hidden");

		String a = StorageKeys.newKey(7, 42, "My Notes.pdf");
		assertThat(a).matches("user_7/task_42/[0-9a-f-]{36}_My_Notes\\.pdf");
		assertThat(StorageKeys.newKey(7, 42, "My Notes.pdf")).isNotEqualTo(a);
	}

}
