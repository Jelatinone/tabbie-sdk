package cat.tabbie.sdk.album;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalLong;

import cat.tabbie.sdk.merchant.Observer;
import lombok.NonNull;

/**
 * Opens source content and retains immutable captured bytes for a specific
 * item. Implementations may acquire remote, local, cached, or generated
 * content. Each successful capture publishes complete bytes; an I/O failure
 * publishes no incomplete content.
 *
 * Captured references are verified by digest and size. Stores may deduplicate
 * bytes across filenames. Retention implementations keep bytes readable until
 * their documented lifetime ends or their owner explicitly reclaims them. Core
 * owns revision/backup retention. Providers document concurrency and limits;
 * this interface supplies no backend. Sources supply open() and, for named-file
 * placement, fileName(). Known catalog checksums are verified by the source;
 * retention stores implement capture, open(reference), and exists(reference).
 */
public interface Store extends AutoCloseable, Describe, Extract {

  /**
   * Checks whether primary content is already locally available, without
   * acquiring it. Sources without local retention return false by default.
   * This is an observation, not a reservation or an installation-state check.
   *
   * @return whether primary content is locally available
   * @throws IOException when availability cannot be checked
   */
  default boolean exists() throws IOException {
    return false;
  }

  /**
   * Consumes the source to EOF and retains complete bytes, leaving the source
   * open. Reports progress for this transfer and then verification or an I/O
   * failure. Observer callbacks must not throw; an unchecked callback failure
   * propagates and does not imply retained content was rolled back. The
   * produced reference is cross-checked against this store's own {@link #of()}
   * evidence when it declares an expected digest or size.
   *
   * @param source   caller-owned stream
   * @param observer transfer callbacks
   * @return verified reference to retained bytes
   * @throws IOException when reading or retention fails
   */
  @NonNull
  default Intermediate<Reference> capture(@NonNull InputStream source,
      @NonNull Observer<? super Reference.Captured, ? super Transfer> observer)
      throws IOException {
    return new Capture(of(), source, observer);
  }

  /**
   * Captures a defensive copy of caller-owned bytes.
   *
   * @param bytes complete content
   * @return retained content reference
   * @throws IOException when retention fails
   */
  @NonNull
  default Intermediate<Reference> capture(@NonNull byte[] bytes) throws IOException {
    return capture(new ByteArrayInputStream(bytes.clone()), Observer.none());
  }

  /**
   * Releases store resources according to its documented lifetime. Installers
   * never close caller-owned stores. Stateless sources need no cleanup.
   *
   * @throws IOException when resources cannot be released
   */
  @Override
  default void close() throws IOException {
  }

  /**
   * Capture effect over an already-open, caller-owned stream.
   *
   * @param pending  expected identity to verify against, if it declares one
   * @param source   caller-owned stream, opened before construction
   * @param observer transfer callbacks
   */
  record Capture(Reference.Pending pending, InputStream source,
      Observer<? super Reference.Captured, ? super Transfer> observer)
      implements Intermediate.Step<Reference> {
    @Override
    public Reference collapse(Intermediate.Context context) throws IOException {
      return digest(pending, source, observer);
    }
  }

  /**
   * Capture effect over a reusable description, opened only during collapse.
   *
   * @param source reusable description of the bytes to capture
   */
  record Extraction(Describe source,
      Observer<? super Reference.Captured, ? super Transfer> observer)
      implements Intermediate.Step<Reference.Captured> {
    @Override
    public Reference.Captured collapse(Intermediate.Context context) throws IOException {
      try (InputStream input = source.open()) {
        return digest(source.of(), input, observer);
      }
    }
  }

  /**
   * Reads a stream to EOF, computing its SHA-256 digest and size, verifying
   * against any expected evidence the description declares.
   *
   * @param pending  expected identity to verify against, if it declares one
   * @param source   stream to consume to EOF
   * @param observer transfer callbacks
   * @return verified reference to the consumed bytes
   * @throws IOException when reading fails or verification does not match
   */
  private static Reference.Captured digest(Reference.Pending pending, InputStream source,
      Observer<? super Reference.Captured, ? super Transfer> observer)
      throws IOException {
    try {
      MessageDigest digest = sha256();
      long size = 0;
      byte[] buffer = new byte[8192];
      for (int read; (read = source.read(buffer)) != -1;) {
        digest.update(buffer, 0, read);
        size = Math.addExact(size, read);
        observer.transfer(Transfer.of(size, pending.expectedSize() != null ? pending.expectedSize() : 0));
      }
      String hash = HexFormat.of().formatHex(digest.digest());
      if (pending.expectedSize() != null && pending.expectedSize() != size) {
        throw new IOException("Captured size does not match expected evidence.");
      }
      if (pending.expectedSha256() != null && !pending.expectedSha256().equals(hash)) {
        throw new IOException("Captured digest does not match expected evidence.");
      }
      Reference.Captured captured = new Reference.Captured(pending.fileName(), hash, size);
      observer.verified(captured);
      return captured;
    } catch (IOException failure) {
      observer.failed(failure);
      throw failure;
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("The Java runtime must provide SHA-256.", failure);
    }
  }

  record Transfer(long transferredSize, OptionalLong expectedSize) {

    public Transfer {
      if (transferredSize < 0) {
        throw new IllegalArgumentException("Transferred bytes must be non-negative");
      }
      if (expectedSize.isPresent() && expectedSize.getAsLong() < 0) {
        throw new IllegalArgumentException("Expected bytes must be non-negative or empty");
      }
    }

    public static Transfer of(long transferred, long expectedOrZero) {
      return new Transfer(
          transferred,
          expectedOrZero > 0 ? OptionalLong.of(expectedOrZero) : OptionalLong.empty());
    }

    public Transfer(long transferredSize, long expectedSize) {
      this(transferredSize, OptionalLong.of(expectedSize));
    }
  }
}
