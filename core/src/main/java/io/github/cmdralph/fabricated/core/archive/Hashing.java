package io.github.cmdralph.fabricated.core.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers. */
public final class Hashing {
	private Hashing() {
	}

	public static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
		}
	}

	public static String sha256(byte[] data) {
		return HexFormat.of().formatHex(sha256Digest().digest(data));
	}

	public static String sha256(String text) {
		return sha256(text.getBytes(StandardCharsets.UTF_8));
	}

	/** Streams a file through SHA-256 without loading it fully into memory. */
	public static String sha256(Path file) throws IOException {
		MessageDigest digest = sha256Digest();
		try (InputStream in = Files.newInputStream(file)) {
			in.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
