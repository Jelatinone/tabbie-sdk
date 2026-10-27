package cat.tabbie.sdk.platform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * The source role: a reusable, inspectable description of how to obtain
 * bytes, and what they are called. Providers describe artifact content this
 * way; a {@link cat.tabbie.sdk.album.repository.Stash Stash} describes
 * retained content this way too, and captures it with a
 * {@link cat.tabbie.sdk.album.repository.Stash.Capture Capture}. Describing
 * performs no I/O; {@link #open} is repeatable and independent of any prior
 * inspection, so a description can be reopened later without retaining an
 * open handle to whatever it was derived from.
 *
 * <p>
 * The name belongs to the description, not to the bytes: the
 * {@link Reference} {@link #of()} declares identifies the bytes by content
 * alone, so identical bytes known by several names share one identity.
 */
public interface Describe {

  /**
   * Locates the described bytes within their source, such as an archive
   * entry's path within its archive. Transfers of the bytes report it.
   *
   * @return path naming a file; its last component is the
   *         {@link #fileName() filename}
   */
  @NonNull
  Address path();

  /**
   * Returns the filename the described bytes are known by, such as the name a
   * file placed from them takes.
   *
   * @return single filename, the last component of {@link #path()}
   */
  @NonNull
  default String fileName() {
    return path().fileName().orElseThrow();
  }

  /**
   * Describes the content without opening it.
   *
   * @return expected identity of the content
   */
  @NonNull
  Reference.Pending of();

  /**
   * Opens the described content.
   *
   * @return independent stream at position zero, owned by the caller
   * @throws IOException when the content is not readable
   */
  @NonNull
  InputStream open() throws IOException;

  /**
   * Copies the content into a sink, verifying any evidence {@link #of()}
   * declares. The sink is left open; on failure it may hold partial bytes.
   *
   * @param sink destination for the content
   * @return identity of the bytes copied
   * @throws IOException when reading or writing fails, or evidence differs
   */
  @NonNull
  default Reference.Captured transfer(@NonNull OutputStream sink) throws IOException {
    return transfer(sink, Observer.none());
  }

  /**
   * Copies the content into a sink to EOF, computing its SHA-256 digest and
   * size and verifying them against any evidence {@link #of()} declares. Each
   * read is reported as a {@link Reference.Transfer} of this description's
   * {@link #path()}. The sink is left open; on failure the observer is
   * notified and the sink may hold partial bytes, which the caller must not
   * publish.
   *
   * @param sink     destination for the content
   * @param observer transfer callbacks
   * @return identity of the bytes copied
   * @throws IOException when reading or writing fails, or evidence differs
   */
  @NonNull
  default Reference.Captured transfer(@NonNull OutputStream sink,
      @NonNull Observer<? super Reference.Captured, ? super Reference.Transfer> observer) throws IOException {
    Reference.Pending expected = of();
    String subject = path().canonical();
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("The Java runtime must provide SHA-256.", failure);
    }
    try (InputStream source = open()) {
      OptionalLong total = expected.expectedSize() != null
          ? OptionalLong.of(expected.expectedSize())
          : OptionalLong.empty();
      long size = 0;
      byte[] buffer = new byte[8192];
      for (int read; (read = source.read(buffer)) != -1;) {
        size = Math.addExact(size, read);
        // Stop at the first byte past the expected size rather than reading on
        if (expected.expectedSize() != null && size > expected.expectedSize()) {
          throw new IOException("Captured size exceeds expected evidence: " + subject);
        }
        digest.update(buffer, 0, read);
        sink.write(buffer, 0, read);
        observer.transfer(new Reference.Transfer(subject, size, total));
      }
      String hash = HexFormat.of().formatHex(digest.digest());
      if (expected.expectedSize() != null && expected.expectedSize() != size) {
        throw new IOException("Captured size does not match expected evidence: " + subject);
      }
      if (expected.expectedSha256() != null && !expected.expectedSha256().equals(hash)) {
        throw new IOException("Captured digest does not match expected evidence: " + subject);
      }
      Reference.Captured captured = new Reference.Captured(hash, size);
      observer.verified(captured);
      return captured;
    } catch (IOException failure) {
      observer.failed(failure);
      throw failure;
    }
  }

  /**
   * Digests the content without keeping it.
   *
   * @return identity of the content
   * @throws IOException when reading fails, or evidence differs
   */
  @NonNull
  default Reference.Captured identify() throws IOException {
    return transfer(OutputStream.nullOutputStream());
  }

  /**
   * Reads the whole content, for content small enough to hold in memory.
   *
   * @return complete content
   * @throws IOException when reading fails, or evidence differs
   */
  @NonNull
  default byte[] bytes() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    transfer(sink);
    return sink.toByteArray();
  }

  /**
   * Reads the whole content as UTF-8 text.
   *
   * @return complete text
   * @throws IOException when reading fails, or evidence differs
   */
  @NonNull
  default String text() throws IOException {
    return new String(bytes(), StandardCharsets.UTF_8);
  }

  /**
   * Describes a private copy of bytes, carrying their digest and size as
   * evidence so whatever writes them verifies exactly these bytes.
   *
   * @param fileName single filename the bytes are known by
   * @param bytes    complete content
   * @return reusable description of the copy
   * @throws IllegalArgumentException when the filename is not a single
   *                                  filename
   */
  static Describe of(@NonNull String fileName, @NonNull byte[] bytes) {
    byte[] copy = bytes.clone();
    return new BytesSource(Address.of(fileName), Reference.of(copy).expected(), copy);
  }

  /**
   * Describes text as UTF-8 bytes, carrying their digest and size as evidence.
   *
   * @param fileName single filename the text is known by
   * @param text     complete text
   * @return reusable description of the text
   * @throws IllegalArgumentException when the filename is not a single
   *                                  filename
   */
  static Describe of(@NonNull String fileName, @NonNull String text) {
    return of(fileName, text.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Describes an already-open, caller-owned stream. It is consumed to EOF and
   * left open. The description is single-use: opening it a second time fails
   * rather than reading an exhausted stream.
   *
   * @param path     path naming the stream's bytes within their source
   * @param expected evidence for the stream's bytes
   * @param source   caller-owned stream
   * @return single-use description of the stream
   */
  static Describe from(@NonNull Address path, @NonNull Reference.Pending expected, @NonNull InputStream source) {
    return new StreamSource(path, expected, source);
  }
}

/**
 * Single-use description of a caller-owned stream, which stays open.
 *
 * @param path   path naming the stream's bytes within their source
 * @param of     expected identity
 * @param source caller-owned stream
 * @param opened whether the stream was already handed out
 */
record StreamSource(Address path, Reference.Pending of, InputStream source, AtomicBoolean opened)
    implements Describe {

  /**
   * Checks that the path names a file.
   *
   * @throws IllegalArgumentException when the path is the root
   */
  StreamSource {
    if (path.isRoot()) {
      throw new IllegalArgumentException("A source path must name a file.");
    }
  }

  /**
   * Single-use description of a caller-owned stream, which stays open.
   *
   * @param path   path naming the stream's bytes within their source
   * @param of     expected identity
   * @param source caller-owned stream
   */
  public StreamSource(Address path, Reference.Pending of, InputStream source) {
    this(path, of, source, new AtomicBoolean());
  }

  @Override
  public InputStream open() throws IOException {
    if (opened.getAndSet(true)) {
      throw new IOException("A captured stream can only be read once.");
    }
    return new FilterInputStream(source) {
      @Override
      public void close() {
      }
    };
  }
}

/**
 * Reusable description of a private copy of bytes.
 *
 * @param path    path naming the bytes
 * @param of      expected identity
 * @param content private copy, never handed out
 */
record BytesSource(Address path, Reference.Pending of, byte[] content) implements Describe {

  /**
   * Checks that the path names a file.
   *
   * @throws IllegalArgumentException when the path is the root
   */
  BytesSource {
    if (path.isRoot()) {
      throw new IllegalArgumentException("A source path must name a file.");
    }
  }

  @Override
  public InputStream open() {
    return new ByteArrayInputStream(content);
  }
}
