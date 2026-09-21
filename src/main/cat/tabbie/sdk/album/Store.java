package cat.tabbie.sdk.album;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;

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
public interface Store extends AutoCloseable {

  /**
   * Opens provider-designated primary content, when the store has one.
   *
   * @return independent stream at position zero, owned by the caller
   * @throws IOException when no primary content is designated or readable
   */
  @NonNull
  InputStream open() throws IOException;

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
   * propagates and does not imply retained content was rolled back.
   *
   * @param source   caller-owned stream
   * @param observer transfer callbacks
   * @return verified reference to retained bytes
   * @throws IOException when reading or retention fails
   */
  @NonNull
  default Reference capture(@NonNull InputStream source, @NonNull Observer observer) throws IOException {
    throw new IOException("Store does not support capture");
  }

  /**
   * Captures bytes without observing progress or closing the source.
   *
   * @param source caller-owned stream
   * @return verified reference to retained bytes
   * @throws IOException when reading or retention fails
   */
  @NonNull
  default Reference capture(@NonNull InputStream source) throws IOException {
    return capture(source, Observer.NONE);
  }

  /**
   * Captures a defensive copy of caller-owned bytes.
   *
   * @param bytes complete content
   * @return retained content reference
   * @throws IOException when retention fails
   */
  @NonNull
  default Reference capture(@NonNull byte[] bytes) throws IOException {
    return capture(new ByteArrayInputStream(bytes.clone()));
  }

  /**
   * Releases store resources according to its documented lifetime. Artists
   * never close caller-owned stores. Stateless sources need no cleanup.
   *
   * @throws IOException when resources cannot be released
   */
  @Override
  default void close() throws IOException {
  }

  /**
   * Releases this store's resources according to its documented lifetime.
   * Artifact image generation never closes caller-owned stores.
   *
   * @throws IOException when resources cannot be released
   */
  interface Observer {

    /**
     * Observer that discards every notification.
     */
    Observer NONE = new Observer() {
    };

    /**
     * Observe in-progress transfer statistics
     *
     * @param transferredSize current total bytes transferred
     * @param expectedSize    expected total bytes transferred
     */
    default void transferred(long transferredSize, @NonNull OptionalLong expectedSize) {
    }

    /**
     * Observe complete transfer verification of a capture
     *
     * @param reference verified capture reference
     */
    default void verified(Reference reference) {
    }

    /**
     * Observe failed transfer verification of a capture
     *
     * @param failure failed capture exception
     */
    default void failed(IOException failure) {
    }
  }
}
