package cat.tabbie.sdk.tree;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.failure.Reason;
import cat.tabbie.sdk.failure.Refusal;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/**
 * A {@link Shape} its maker declares, rather than one a filesystem or an
 * archive backs: content's bytes as a provider describes them. A source is
 * read-only, and nothing is crawled to find it; whoever builds one says what
 * it holds. Bytes held in memory are a {@link File}; downloads named by their
 * endpoints, and directories of sources, join in phase 6.
 */
public sealed interface Source extends Shape permits Source.File {

  /**
   * Returns what a source allows: reading alone.
   *
   * @return {@link Rule.Permission#READABLE}
   */
  @Override
  default Set<Rule.Permission> permissions() {
    return Set.of(Rule.Permission.READABLE);
  }

  /**
   * Declares bytes held in memory.
   *
   * @param name  portable filename the bytes are known by
   * @param bytes the bytes, copied
   * @return the source
   * @throws IllegalArgumentException when the name is not a portable single
   *                                  filename
   */
  static Source.File of(@NonNull String name, @NonNull byte[] bytes) {
    return new Source.File(name, bytes);
  }

  /**
   * Declares text held in memory, as UTF-8 bytes.
   *
   * @param name portable filename the text is known by
   * @param text the text
   * @return the source
   * @throws IllegalArgumentException when the name is not a portable single
   *                                  filename
   */
  static Source.File of(@NonNull String name, @NonNull String text) {
    return of(name, text.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * A file of bytes held in memory, standing alone at its name.
   *
   * @param name    portable filename the bytes are known by
   * @param content the bytes, never handed out
   */
  record File(@NonNull String name, @NonNull byte[] content) implements Source, Shape.File {

    /**
     * Copies the bytes and checks the name.
     *
     * @throws IllegalArgumentException when the name is not a portable single
     *                                  filename
     */
    public File {
      if (!Address.portable(name)) {
        throw new IllegalArgumentException(String.format("\"%s\" is not a portable single filename.", name));
      }
      content = content.clone();
    }

    @Override
    public Address address() {
      return Address.of(name);
    }

    @Override
    public Optional<Shape.Directory> parent() {
      return Optional.empty();
    }

    @Override
    public State state() {
      return new State.File(describe());
    }

    @Override
    public Describe describe() {
      return Describe.of(name, content, this);
    }

    /**
     * Returns a copy of the bytes.
     *
     * @return the bytes
     */
    @Override
    public byte[] content() {
      return content.clone();
    }

    @Override
    public Lease.Read read() {
      return new Held(this);
    }

    /**
     * Refuses, since a source is read-only.
     *
     * @return nothing
     * @throws Refusal with {@link Reason.Place.Denied}, always
     */
    @Override
    public Lease.Write write() throws Refusal {
      throw new Refusal(new Reason.Place.Denied(address(), Rule.Permission.EDITABLE));
    }

    /**
     * A read lease on bytes held in memory, which never change and exclude
     * nothing.
     */
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static final class Held implements Lease.Read {

      Source.File file;

      @NonFinal
      volatile boolean closed;

      Held(Source.File file) {
        this.file = file;
      }

      private byte[] content() {
        if (closed) {
          throw new IllegalStateException("A closed lease cannot be used.");
        }
        return file.content;
      }

      @Override
      public Address address() {
        return file.address();
      }

      @Override
      public long size() {
        return content().length;
      }

      @Override
      public int read(long position, @NonNull ByteBuffer target) {
        return Lease.Read.copy(content(), position, target);
      }

      @Override
      public void close() {
        closed = true;
      }
    }
  }
}
