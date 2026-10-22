package com.justdoit.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

// Codes for email verification and password reset, e.g. "K7Q29XMD4TBW" (shown to users as "K7Q2 9XMD 4TBW")
public final class OneTimeCodes {

	// No 0/O, 1/I/L or U, so a code read off a screen can't be mistyped
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
	private static final int LENGTH = 12; // 30^12 ~ 2^59 possibilities: unguessable within a 30-minute expiry
	private static final SecureRandom RANDOM = new SecureRandom();

	private OneTimeCodes() {
	}

	public static String generate() {
		StringBuilder code = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	// "k7q2 9xmd-4tbw" -> "K7Q29XMD4TBW": users may paste the grouped form (UI plan 7.4)
	public static String normalize(String code) {
		return code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
	}

	public static String group(String code) {
		return code.replaceAll("(.{4})(?!$)", "$1 ");
	}

	// Only this hash is stored. A fast hash is fine here (unlike passwords) because the code is long and random.
	public static String hash(String code) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalize(code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available on the JVM", e);
		}
	}

}
