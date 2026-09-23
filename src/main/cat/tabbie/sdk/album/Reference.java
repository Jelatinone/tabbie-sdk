package cat.tabbie.sdk.album;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import lombok.NonNull;

/**
 * Identity of immutable bytes, independent of their storage location.
 */
public sealed interface Reference {

  String fileName();

  /**
   * Checks a single filename, a lowercase digest, and a nonnegative byte count.
   */

  record Pending(
      @NonNull String fileName,

      @NonNull Path path,
      String expectedSha256,
      Long expectedSize) implements Reference {
    public Pending {
      validateFilename(fileName);

      path = path.normalize();
      if (path.isAbsolute() || path.startsWith("..")) {
        throw new IllegalArgumentException("Expected a safe relative source path.");
      }
      if (expectedSize != null && expectedSize < 0L) {
        throw new IllegalArgumentException("Content size must be non-negative");
      }
      if (expectedSha256 != null && !expectedSha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest");
      }
    }
  }

  record Captured(
      @NonNull String fileName,

      @NonNull String sha256,
      long size) implements Reference {
    public Captured {
      validateFilename(fileName);

      if (size < 0L) {
        throw new IllegalArgumentException("Content size must be non-negative");
      }
      if (!sha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest");
      }
    }
  }

  /**
   * Checks a single filename without reading the filesystem. These lexical
   * checks do not establish whether a target filesystem permits the name.
   *
   * @param fileName filename to check
   * @throws IllegalArgumentException when the name is blank or contains a path
   */
  public static void validateFilename(@NonNull String fileName) {
    if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
        || fileName.chars().anyMatch(character -> character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0)
        || Path.of(fileName).isAbsolute() || Path.of(fileName).getNameCount() != 1) {
      throw new IllegalArgumentException("Expected a non-blank single filename.");
    }
  }

  /**
   * Computes a reference without retaining or modifying the supplied bytes.
   *
   * @param fileName single filename
   * @param bytes    content to identify
   * @return SHA-256 reference and exact size
   */
  public static Reference of(@NonNull String fileName, @NonNull byte[] bytes) {
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
