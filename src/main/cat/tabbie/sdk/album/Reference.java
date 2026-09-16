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
 * @param sha256 lowercase SHA-256 hexadecimal digest
 * @param size   exact byte count
 */
public record Reference(@NonNull String sha256, long size) {

  public Reference {
    if (!sha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest.");
    }
    if (size < 0L) {
      throw new IllegalArgumentException("Content size must be non-negative.");
    }
  }

  /**
   * Computes a reference without retaining or modifying the supplied bytes.
   *
   * @param bytes content to identify
   * @return SHA-256 reference and exact size
   */
  public static Reference of(@NonNull byte[] bytes) {
    try {
      return new Reference(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), bytes.length);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("The Java runtime must provide SHA-256.", failure);
    }
  }
}
