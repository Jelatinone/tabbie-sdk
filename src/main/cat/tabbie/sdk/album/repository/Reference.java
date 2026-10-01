package cat.tabbie.sdk.album.repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * Identity of immutable bytes, independent of their storage location. A
 * {@link Pending} reference describes bytes still to be read, with optional
 * expected evidence; a {@link Captured} reference describes complete bytes
 * whose digest and size were verified.
 */
public sealed interface Reference permits Reference.Pending, Reference.Captured {

	/**
	 * Returns the single filename used for named-file placement.
	 *
	 * @return filename
	 */
	@NonNull
	String fileName();

	/**
	 * Expected identity of bytes that have not been read yet.
	 *
	 * @param fileName       single filename, equal to the last path component
	 * @param path           canonical forward-slash source path, such as an
	 *                       archive entry path
	 * @param expectedSha256 lowercase SHA-256 digest the bytes must match, or
	 *                       null when unknown
	 * @param expectedSize   exact byte count the bytes must match, or null when
	 *                       unknown
	 */
	record Pending(
			@NonNull String fileName,
			@NonNull String path,
			String expectedSha256,
			Long expectedSize) implements Reference {

		/**
		 * Checks the filename, the canonical path, and any expected evidence.
		 *
		 * @throws IllegalArgumentException when the path is not canonical, its last
		 *                                  component differs from the filename, or
		 *                                  the evidence is malformed
		 */
		public Pending {
			Relative.validateFilename(fileName);
			List<String> components = Relative.decode(path);
			if (components.isEmpty() || !components.getLast().equals(fileName)) {
				throw new IllegalArgumentException("A source path must end with its filename.");
			}
			if (expectedSize != null && expectedSize < 0L) {
				throw new IllegalArgumentException("Content size must be non-negative");
			}
			if (expectedSha256 != null && !expectedSha256.matches("[0-9a-f]{64}")) {
				throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest");
			}
		}

		/**
		 * Describes a top-level file with known or unknown evidence.
		 *
		 * @param fileName       single filename
		 * @param expectedSha256 expected digest, or null when unknown
		 * @param expectedSize   expected byte count, or null when unknown
		 */
		public Pending(@NonNull String fileName, String expectedSha256, Long expectedSize) {
			this(fileName, fileName, expectedSha256, expectedSize);
		}

		/**
		 * Describes a top-level file without expected evidence.
		 *
		 * @param fileName single filename
		 */
		public Pending(@NonNull String fileName) {
			this(fileName, fileName, null, null);
		}
	}

	/**
	 * Complete bytes verified by digest and size.
	 *
	 * @param fileName single filename
	 * @param sha256   lowercase SHA-256 digest
	 * @param size     exact byte count
	 */
	record Captured(
			@NonNull String fileName,
			@NonNull String sha256,
			long size) implements Reference {

		/**
		 * Checks a single filename, a lowercase digest, and a non-negative byte
		 * count.
		 */
		public Captured {
			Relative.validateFilename(fileName);
			if (size < 0L) {
				throw new IllegalArgumentException("Content size must be non-negative");
			}
			if (!sha256.matches("[0-9a-f]{64}")) {
				throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest");
			}
		}

		/**
		 * Describes these retained bytes as pending evidence, so a reopened copy
		 * can be verified against them.
		 *
		 * @return pending reference with this digest and size
		 */
		public Pending expected() {
			return new Pending(fileName, sha256, size);
		}
	}

	/**
	 * Computes a reference without retaining or modifying the supplied bytes.
	 *
	 * @param fileName single filename
	 * @param bytes    content to identify
	 * @return SHA-256 reference and exact size
	 */
	public static Captured of(@NonNull String fileName, @NonNull byte[] bytes) {
		try {
			return new Captured(
					fileName,
					HexFormat.of()
							.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
					bytes.length);
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("The Java runtime must provide SHA-256.", failure);
		}
	}
}