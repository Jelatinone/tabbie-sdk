package cat.tabbie.sdk.album;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import lombok.NonNull;

/**
 * <h1>Reference</h1>
 *
 * Identity of immutable bytes, independent of their storage location.
 *
 * @param fileName name in file system
 * @param size     exact byte count
 */
public sealed interface Reference {

	@NonNull
	String fileName();

	long size();

	public record Pending(@NonNull String fileName, long size) implements Reference {

		public Pending {
			if (fileName().isBlank()) {
				throw new IllegalArgumentException("Content file name must be non-blank");
			}
			if (size < 0L) {
				throw new IllegalArgumentException("Content size must be non-negative");
			}
		}
	}

	public record Captured(@NonNull String fileName, @NonNull String sha256, long size) implements Reference {

		public Captured {
			if (fileName().isBlank()) {
				throw new IllegalArgumentException("Content file name must be non-blank");
			}
			if (size < 0L) {
				throw new IllegalArgumentException("Content size must be non-negative");
			}
			if (!sha256.matches("[0-9a-f]{64}")) {
				throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest");
			}
		}

		/**
		 * Computes a reference without retaining or modifying the supplied bytes.
		 *
		 * @param bytes content to identify
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

}
