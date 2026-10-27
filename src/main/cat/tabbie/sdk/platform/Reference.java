package cat.tabbie.sdk.platform;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.OptionalLong;

import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * Identity of immutable bytes by their content alone, independent of where
 * they are stored and of any name they are known by. A {@link Pending}
 * reference describes bytes still to be read, with optional expected evidence;
 * a {@link Captured} reference describes complete bytes whose digest and size
 * were verified. Where bytes lie in a tree is a node's to say, and what a
 * source calls them is its {@link Describe description's}.
 */
public sealed interface Reference permits Reference.Pending, Reference.Captured {

  /**
   * Expected identity of bytes that have not been read yet.
   *
   * @param expectedSha256 lowercase SHA-256 digest the bytes must match, or
   *                       null when unknown
   * @param expectedSize   exact byte count the bytes must match, or null when
   *                       unknown
   */
  record Pending(String expectedSha256, Long expectedSize) implements Reference {

    /**
     * Checks any expected evidence.
     *
     * @throws IllegalArgumentException when the evidence is malformed
     */
    public Pending {
      if (expectedSize != null && expectedSize < 0L) {
        throw new IllegalArgumentException("Content size must be non-negative");
      }
      if (expectedSha256 != null && !expectedSha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest");
      }
    }

    /**
     * Describes bytes without expected evidence.
     */
    public Pending() {
      this(null, null);
    }
  }

  /**
   * Complete bytes verified by digest and size.
   *
   * @param sha256 lowercase SHA-256 digest
   * @param size   exact byte count
   */
  record Captured(@NonNull String sha256, long size) implements Reference {

    /**
     * Checks a lowercase digest and a non-negative byte count.
     *
     * @throws IllegalArgumentException when the digest or count is malformed
     */
    public Captured {
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
      return new Pending(sha256, size);
    }
  }

  /**
   * Progress of one transfer of bytes, such as a capture, as reported to an
   * observer. A transfer that has read all it expected, or whose total is
   * unknown, renders as the amount read so far.
   *
   * @param subject         what is being transferred, such as a file name or
   *                        an archive entry's path
   * @param transferredSize bytes read so far
   * @param expectedSize    total expected bytes, when known
   */
  record Transfer(
      @NonNull String subject,
      long transferredSize,
      @NonNull OptionalLong expectedSize) implements Observer.Renderable {

    /**
     * Checks non-negative counts.
     *
     * @throws IllegalArgumentException when a count is negative
     */
    public Transfer {
      if (transferredSize < 0) {
        throw new IllegalArgumentException("Transferred bytes must be non-negative.");
      }
      if (expectedSize.isPresent() && expectedSize.getAsLong() < 0) {
        throw new IllegalArgumentException("Expected bytes must be non-negative or empty.");
      }
    }

    @Override
    public String render() {
      String read = Observer.Operand.BYTE.render(transferredSize);
      if (expectedSize.isEmpty() || expectedSize.getAsLong() == transferredSize) {
        return String.format("%s  %s", subject, read);
      }
      long total = expectedSize.getAsLong();
      return String.format(Locale.ROOT, "%s  %s of %s (%d%%)", subject, read, Observer.Operand.BYTE.render(total),
          total == 0 ? 100 : transferredSize * 100 / total);
    }
  }

  /**
   * Computes a reference without retaining or modifying the supplied bytes.
   *
   * @param bytes content to identify
   * @return SHA-256 reference and exact size
   */
  static Captured of(@NonNull byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return new Captured(HexFormat.of().formatHex(digest.digest(bytes)), bytes.length);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("The Java runtime must provide SHA-256.", failure);
    }
  }
}
