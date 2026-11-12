package cat.tabbie.sdk.content;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cat.tabbie.sdk.model.Layout;
import cat.tabbie.sdk.plate.Archive;
import cat.tabbie.sdk.plate.Plate;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Observer;
import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.revision.Image;
import cat.tabbie.sdk.tree.Address;
import cat.tabbie.sdk.tree.Violation;
import lombok.NonNull;

/**
 * Everything an action may do with bytes: capture them into the stash of the
 * work it runs in, inspect and unpack archives within core's limits, describe
 * what was captured, and report as it goes. An action's context holds a
 * capture and never a {@link Plate}, a node, a path or a lease, so an action
 * reaches no file of the Cat: it only describes images, which core exposes.
 *
 * <p>
 * An archive's files are read as {@link Entry entries}, and an archive refused
 * for its content throws a {@link Rejection}, as content an action finds
 * malformed may too.
 *
 * <p>
 * Capturing changes no file beneath the Cat's root, so it runs alike while
 * rehearsing and acting; the scratch space unpacking uses is core's, and never
 * a Cat's files.
 */
public interface Capture {

  /**
   * Creates the capture of work running on a plate: it captures into the
   * plate's stash, unpacks in its scratch space, reports to its observer, and
   * processes archives within core's limits. Core creates it; the plate stays
   * out of the action's reach.
   *
   * @param plate  plate the work runs on
   * @param limits limits archives are processed within
   * @return the capture
   */
  static Capture of(@NonNull Plate plate, @NonNull Archive limits) {
    return new PlateCapture(plate, limits);
  }

  /**
   * Captures a source into the stash of the work, reporting to its observer as
   * it runs.
   *
   * @param source description of the bytes to capture
   * @return verified reference to the retained bytes
   * @throws IOException when the source cannot be read, its evidence differs,
   *                     or the bytes cannot be retained
   */
  @NonNull
  Reference.Captured capture(@NonNull Describe source) throws IOException;

  /**
   * Captures an archive, then inspects its central directory within core's
   * limits.
   *
   * @param archive archive to inspect, named by its source
   * @return the archive's file entries
   * @throws IOException when the archive cannot be captured or is refused
   */
  @NonNull
  List<Entry> inspect(@NonNull Describe archive) throws IOException;

  /**
   * Unpacks an archive within core's limits into several destinations beneath
   * a directory of a model, inspecting it once and capturing each of its files
   * once, however many destinations receive it.
   *
   * @param archive      archive to unpack, named by its source
   * @param directory    directory of a model to unpack beneath
   * @param destinations addresses of the extraction directories relative to
   *                     it, each receiving every file
   * @return immutable images creating the archive's files in every
   *         destination
   * @throws IOException when the archive cannot be captured or is refused
   * @throws Violation   when the model does not allow a file where the archive
   *                     puts it
   */
  @NonNull
  Set<Image<?>> unpack(
      @NonNull Describe archive,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException;

  /**
   * Unpacks the inspected entries of an archive that lie beneath a folder of
   * it, each at its path relative to that folder, into several destinations,
   * as {@link #unpack(Describe, Layout.Directory, Set)} does with every entry.
   * Work that reads an archive's entries before unpacking it unpacks them this
   * way, without capturing the archive again.
   *
   * @param entries      file entries of one archive, as
   *                     {@link #inspect(Describe)} found them
   * @param from         folder of the archive to unpack from, the root address
   *                     for the whole archive
   * @param directory    directory of a model to unpack beneath
   * @param destinations addresses of the extraction directories relative to
   *                     it, each receiving every file unpacked
   * @return immutable images creating the files in every destination
   * @throws IOException              when the archive cannot be reopened or is
   *                                  refused
   * @throws Violation                when the model does not allow a file
   *                                  where it is unpacked
   * @throws IllegalArgumentException when no entry lies beneath the folder
   */
  @NonNull
  Set<Image<?>> unpack(
      @NonNull List<Entry> entries,
      @NonNull Address from,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException;

  /**
   * Describes bytes the work captured, by reference. Describing performs no
   * I/O; opening the result yields exactly the captured bytes.
   *
   * @param captured reference a capture of the work returned
   * @return reusable description of the retained bytes
   */
  @NonNull
  Describe retained(@NonNull Reference.Captured captured);

  /**
   * Returns the observer of the work, which every step shares, so an action
   * can report its own progress beside the captures it makes.
   *
   * @return observer of the work
   */
  @NonNull
  Observer<? super Observer.Renderable, ? super Observer.Renderable> observer();

  /**
   * Immutable description of one file entry from an inspected archive. No open
   * ZIP handle survives inspection; each {@link #open()} rescans the parent
   * archive, failing when its local entries differ from the inspected catalog,
   * and checks the entry's size and CRC when the stream reaches EOF. Stored
   * entries that record their sizes after their data cannot be rescanned and
   * fail to open; unpacking still extracts them.
   *
   * @param archive parent archive description
   * @param path    entry's path within the archive
   * @param size    exact expanded size
   * @param crc     expected CRC32
   * @param catalog validated central-directory names and expanded sizes
   */
  record Entry(@NonNull Describe archive, @NonNull Address path, long size, long crc,
      @NonNull Map<String, Long> catalog) implements Describe {
    /**
     * Copies the catalog and checks portable entry coordinates and integrity
     * evidence.
     *
     * @throws IllegalArgumentException when the path is the root, the entry
     *                                  disagrees with the catalog, or the size
     *                                  or CRC is out of range
     */
    public Entry {
      if (path.isRoot()) {
        throw new IllegalArgumentException("A ZIP entry needs a path.");
      }
      catalog = Map.copyOf(catalog);
      if (!Long.valueOf(size).equals(catalog.get(path.canonical()))) {
        throw new IllegalArgumentException("ZIP entry must agree with its inspected catalog.");
      }
      if (size < 0 || crc < 0 || crc > 0xffffffffL) {
        throw new IllegalArgumentException("Invalid ZIP entry evidence.");
      }
    }

    @Override
    public Reference.Pending of() {
      return new Reference.Pending(null, size);
    }

    @Override
    public InputStream open() throws IOException {
      ZipInputStream zip = new ZipInputStream(archive.open());
      try {
        Set<String> visited = new HashSet<>();
        for (ZipEntry current; (current = zip.getNextEntry()) != null;) {
          Long expectedSize = catalog.get(current.getName());
          if (expectedSize == null || !visited.add(current.getName())) {
            throw new Rejection.Malformed("ZIP local entries differ from the inspected catalog.", null);
          }
          if (current.getName().equals(path.canonical())) {
            return verified(zip);
          }
          long skipped = skip(zip, expectedSize);
          if (skipped != expectedSize) {
            throw new Rejection.Malformed("ZIP skipped entry size differs from the inspected catalog.", null);
          }
        }
        throw new Rejection.Malformed("Retained ZIP entry is missing: " + path.canonical(), null);
      } catch (IOException | RuntimeException failure) {
        try {
          zip.close();
        } catch (IOException closeFailure) {
          failure.addSuppressed(closeFailure);
        }
        throw failure;
      }
    }

    /**
     * Reads this entry's bytes from a stream of them, however its archive was
     * opened, failing when they exceed the entry's size, or differ from its
     * size or CRC once the stream ends. Closing it before the end reads the
     * rest, so the check still runs. {@link #open()} reads through it as
     * well.
     *
     * @param source stream of this entry's bytes, which the result closes
     * @return the bytes, verified as they are read
     */
    @NonNull
    public InputStream verified(@NonNull InputStream source) {
      return new FilterInputStream(source) {
        private final CRC32 checksum = new CRC32();
        private long count;
        private boolean finished;
        private boolean failed;

        @Override
        public int read() throws IOException {
          byte[] one = new byte[1];
          return read(one, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(one[0]);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
          int read;
          try {
            read = in.read(bytes, offset, length);
          } catch (IOException failure) {
            failed = true;
            throw failure;
          }
          if (read > 0) {
            count += read;
            if (count > size) {
              failed = true;
              throw new Rejection.Malformed("ZIP entry size exceeded.", null);
            }
            checksum.update(bytes, offset, read);
          } else if (read == -1 && !finished) {
            finished = true;
            if (count != size || checksum.getValue() != crc) {
              throw new Rejection.Malformed("ZIP entry size or CRC mismatch: " + path.canonical(), null);
            }
          }
          return read;
        }

        @Override
        public void close() throws IOException {
          try {
            if (!finished && !failed) {
              transferTo(OutputStream.nullOutputStream());
            }
          } finally {
            in.close();
          }
        }
      };
    }
  }

  /**
   * Thrown when an archive is refused rather than unreadable: it breaks the
   * limits it is processed within, names an unsafe path, needs a feature that
   * is not supported, or is malformed. The same bytes are refused again, so a
   * rejection is reported rather than retried, unlike other I/O failures.
   */
  abstract sealed class Rejection extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Describes a refused archive.
     *
     * @param message sentence saying why the archive is refused
     * @param cause   failure that revealed it, or null
     */
    protected Rejection(@NonNull String message, Throwable cause) {
      super(message, cause);
    }

    /**
     * The archive breaks a limit it is processed within: its compressed
     * size, its number of entries, or its expanded size. A caller may retry
     * with wider limits.
     */
    public static final class Exceeded extends Rejection {

      @Serial
      private static final long serialVersionUID = 1L;

      /**
       * Describes an archive beyond its limits.
       *
       * @param message sentence naming the limit broken
       */
      public Exceeded(@NonNull String message) {
        super(message, null);
      }
    }

    /**
     * An entry's path is unsafe to extract, such as one that leaves the
     * extraction directory.
     */
    public static final class Unsafe extends Rejection {

      @Serial
      private static final long serialVersionUID = 1L;

      /**
       * Describes an archive with an unsafe entry path.
       *
       * @param message sentence naming the entry
       * @param cause   failure that revealed it, or null
       */
      public Unsafe(@NonNull String message, Throwable cause) {
        super(message, cause);
      }
    }

    /**
     * An entry needs a feature that is not supported: one this SDK cannot
     * read, such as its compression method, or one the target the archive is
     * installed on cannot load, such as a pack's version written as a string
     * on a server that reads only numbers. Either way the same archive is
     * refused on the same target again.
     */
    public static final class Unsupported extends Rejection {

      @Serial
      private static final long serialVersionUID = 1L;

      /**
       * Describes an archive needing an unsupported feature.
       *
       * @param message sentence naming the entry and the feature
       */
      public Unsupported(@NonNull String message) {
        super(message, null);
      }
    }

    /**
     * The archive is not a valid ZIP, its structure cannot be extracted, such
     * as a file also used as a directory, or its content differs from its
     * catalog.
     */
    public static final class Malformed extends Rejection {

      @Serial
      private static final long serialVersionUID = 1L;

      /**
       * Describes a malformed archive.
       *
       * @param message sentence saying what is malformed
       * @param cause   failure that revealed it, or null
       */
      public Malformed(@NonNull String message, Throwable cause) {
        super(message, cause);
      }
    }
  }

  /**
   * Reads the rest of an entry, refusing more bytes than the size its
   * archive's catalog gives it.
   */
  private static long skip(InputStream entry, long size) throws IOException {
    byte[] bytes = new byte[8192];
    long skipped = 0;
    for (int read; (read = entry.read(bytes)) != -1;) {
      skipped += read;
      if (skipped > size) {
        throw new Rejection.Exceeded("ZIP byte limit exceeded.");
      }
    }
    return skipped;
  }
}

/**
 * The capture of work running on a plate, which it keeps out of reach: no
 * module but the SDK can name this type.
 *
 * @param plate  plate the work runs on
 * @param limits limits archives are processed within
 */
record PlateCapture(@NonNull Plate plate, @NonNull Archive limits) implements Capture {

  @Override
  public Reference.Captured capture(@NonNull Describe source) throws IOException {
    return plate.capture(source);
  }

  @Override
  public List<Entry> inspect(@NonNull Describe archive) throws IOException {
    return limits.inspect(archive, plate);
  }

  @Override
  public Set<Image<?>> unpack(
      @NonNull Describe archive,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException {
    return limits.unpack(archive, directory, destinations, plate);
  }

  @Override
  public Set<Image<?>> unpack(
      @NonNull List<Entry> entries,
      @NonNull Address from,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException {
    return limits.unpack(entries, from, directory, destinations, plate);
  }

  @Override
  public Describe retained(@NonNull Reference.Captured captured) {
    return plate.retained(captured);
  }

  @Override
  public Observer<? super Observer.Renderable, ? super Observer.Renderable> observer() {
    return plate.observer();
  }
}
