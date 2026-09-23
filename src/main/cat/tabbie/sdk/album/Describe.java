package cat.tabbie.sdk.album;

import java.io.IOException;
import java.io.InputStream;

import lombok.NonNull;

/**
 * A reusable, inspectable description of how to produce derived bytes.
 * Describing performs no I/O; {@link #open} is repeatable and independent
 * of any prior inspection, so a description can be reopened later without
 * retaining an open handle to whatever it was derived from.
 */
public interface Describe {

  /**
   * Describes this store's own primary content without opening it.
   *
   * @return expected identity of this store's primary content
   */
  Reference.Pending of();

  /**
   * Opens provider-designated primary content, when the store has one.
   *
   * @return independent stream at position zero, owned by the caller
   * @throws IOException when no primary content is designated or readable
   */
  @NonNull
  InputStream open() throws IOException;
}
