package cat.tabbie.sdk.album.repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * The source role: a reusable, inspectable description of how to obtain
 * bytes. Providers describe artifact content this way; a {@link Stash}
 * describes retained content this way too. Describing performs no I/O;
 * {@link #open} is repeatable and independent of any prior inspection, so a
 * description can be reopened later without retaining an open handle to
 * whatever it was derived from.
 */
public interface Describe {

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
    try (InputStream input = open()) {
      return of().transfer(input, sink, Observer.none());
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
   * Describes capturing this content into the stash of whatever plate
   * collapses the result, reporting its transfer to the plate's observer.
   * Nothing is read until then.
   *
   * @return unevaluated capture, passing with the captured reference
   */
  @NonNull
  default Intermediate<Reference.Captured> capture() {
    return new Stash.Capture(this);
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
    return new BytesSource(Reference.of(fileName, copy).expected(), copy);
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
   * @param expected identity and evidence for the stream's bytes
   * @param source   caller-owned stream
   * @return single-use description of the stream
   */
  static Describe from(@NonNull Reference.Pending expected, @NonNull InputStream source) {
    return new StreamSource(expected, source, new AtomicBoolean());
  }
}

/**
 * Single-use description of a caller-owned stream, which stays open.
 *
 * @param of     expected identity
 * @param source caller-owned stream
 * @param opened whether the stream was already handed out
 */
record StreamSource(Reference.Pending of, InputStream source, AtomicBoolean opened) implements Describe {

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
 * @param of      expected identity
 * @param content private copy, never handed out
 */
record BytesSource(Reference.Pending of, byte[] content) implements Describe {

  @Override
  public InputStream open() {
    return new ByteArrayInputStream(content);
  }
}
