package cat.tabbie.sdk.album;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.NonNull;

/**
 * <h1>Store</h1>
 *
 * Retains immutable content addressed by digest and size. Successful capture
 * publishes complete bytes atomically; a failed capture publishes nothing.
 * Implementations verify references, support independent repeated reads, and
 * keep content available for as long as their documented storage lifetime.
 */
public interface Store {

  /**
   * Retains bytes without closing the caller-owned stream.
   *
   * @param source stream consumed to EOF
   * @return identity of the retained bytes
   * @throws IOException when reading or retention fails
   */
  Reference capture(InputStream source) throws IOException;

  /**
   * Opens verified content. The caller closes the returned independent stream.
   *
   * @param content exact digest and size to retrieve
   * @return readable content at position zero
   * @throws IOException when content is absent, corrupt, or unreadable
   */
  InputStream open(Reference content) throws IOException;

  /**
   * Captures a local file and closes the stream opened by this method.
   *
   * @param source file to read
   * @return retained content reference
   * @throws IOException when opening, reading, closing, or retention fails
   */
  default Reference capture(@NonNull Path source) throws IOException {
    try (InputStream input = Files.newInputStream(source)) {
      return capture(input);
    }
  }

  /**
   * Captures a defensive copy of caller-owned bytes.
   *
   * @param bytes complete content
   * @return retained content reference
   * @throws IOException when retention fails
   */
  default Reference capture(@NonNull byte[] bytes) throws IOException {
    return capture(new ByteArrayInputStream(bytes.clone()));
  }
}
