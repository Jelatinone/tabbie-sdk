package cat.tabbie.sdk.album.repository;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import cat.tabbie.sdk.album.model.Plate;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.album.revision.Scrutiny;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.platform.layout.Layout;
import cat.tabbie.sdk.platform.layout.Lease;
import cat.tabbie.sdk.platform.layout.Node;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Bounds ZIP processing. Default limits permit 100,000 entries, 1 GiB
 * compressed and 4 GiB expanded. Inspecting and unpacking with a plate run at
 * once; without one, they describe steps that run when collapsed. Scratch
 * files exist only while an archive is inspected or unpacked and are never a
 * Cat's files. An archive refused for its content throws a {@link Rejection},
 * which retrying does not help.
 *
 * @param entries       maximum directory and file entries
 * @param archiveBytes  maximum compressed bytes
 * @param expandedBytes maximum total expanded bytes
 */
public record Archive(int entries, long archiveBytes, long expandedBytes) {

  /**
   * Default processing limits.
   */
  public static final Archive DEFAULT = new Archive(100_000, 1L << 30, 4L << 30);

  /**
   * Requires positive limits.
   */
  public Archive {
    if (entries < 1 || archiveBytes < 1 || expandedBytes < 1) {
      throw new IllegalArgumentException("ZIP limits must be positive.");
    }
  }

  /**
   * Inspects the archive's central directory, then captures every file entry
   * and assembles the complete image set.
   * Supports stored/deflated ZIP and ZIP64. Rejects traversal, duplicates,
   * file/directory collisions, unsupported compression, encryption, corrupt
   * data, exceeded bounds, and archives without files. Parent directories
   * need not be listed. Empty files are preserved; empty directories and
   * filesystem metadata are not installed.
   *
   * <p>
   * The archive is read twice, once to inspect it and once to capture its file
   * entries, so it is first captured into the plate's stash: both reopen it
   * from there, and its entries are captured into the same stash. Every file
   * is created beneath a destination in a directory of a model, which must
   * allow creating it there; whatever already exists there is found when the
   * images are exposed. Streams opened while unpacking are closed.
   *
   * @param archive     archive to unpack, named by its source
   * @param directory   directory of a model to unpack beneath
   * @param destination address of the extraction directory relative to it,
   *                    the root address for the directory itself
   * @param plate       plate whose stash keeps the archive and its entries
   * @return immutable images creating the archive's files
   * @throws IOException when the archive cannot be captured or is refused
   * @throws Violation   when the model does not allow a file where the
   *                     archive puts it
   */
  public Set<Image<?>> unpack(@NonNull Describe archive, @NonNull Layout.Directory directory,
      @NonNull Address destination, @NonNull Plate plate) throws IOException {
    return new Unpack(directory, destination, inspect(archive, plate), this).unpack(plate);
  }

  /**
   * Unpacks an archive into several destinations at once, as
   * {@link #unpack(Describe, Layout.Directory, Address, Plate)} does into one.
   * The archive is inspected once and each entry captured once, however many
   * destinations receive it.
   *
   * @param archive      archive to unpack, named by its source
   * @param directory    directory of a model to unpack beneath
   * @param destinations addresses of the extraction directories relative to
   *                     it, each receiving every file
   * @param plate        plate whose stash keeps the archive and its entries
   * @return immutable images creating the archive's files in every
   *         destination
   * @throws IOException when the archive cannot be captured or is refused
   * @throws Violation   when the model does not allow a file where the
   *                     archive puts it
   */
  public Set<Image<?>> unpack(@NonNull Describe archive, @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations, @NonNull Plate plate) throws IOException {
    return new Unpack(directory, destinations, inspect(archive, plate), this).unpack(plate);
  }

  /**
   * Describes unpacking an archive for work that runs later: collapsing it
   * unpacks as {@link #unpack(Describe, Layout.Directory, Address, Plate)} does
   * on the collapsing plate, and a file the model does not allow fails with
   * its violation rather than throwing it.
   *
   * @param archive     archive to unpack, named by its source
   * @param directory   directory of a model to unpack beneath
   * @param destination address of the extraction directory relative to it,
   *                    the root address for the directory itself
   * @return unevaluated unpacking producing an immutable set
   */
  public Intermediate<Set<Image<?>>> unpack(@NonNull Describe archive, @NonNull Layout.Directory directory,
      @NonNull Address destination) {
    return inspect(archive).flatMap(found -> new Unpack(directory, destination, found, this));
  }

  /**
   * Describes unpacking an archive into several destinations for work that
   * runs later, as {@link #unpack(Describe, Layout.Directory, Address)} does
   * into one.
   *
   * @param archive      archive to unpack, named by its source
   * @param directory    directory of a model to unpack beneath
   * @param destinations addresses of the extraction directories relative to
   *                     it, each receiving every file
   * @return unevaluated unpacking producing an immutable set
   */
  public Intermediate<Set<Image<?>>> unpack(@NonNull Describe archive, @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) {
    return inspect(archive).flatMap(found -> new Unpack(directory, destinations, found, this));
  }

  /**
   * Captures an archive, then inspects its central directory using
   * execution-owned scratch space, independent of extraction.
   *
   * @param archive archive to inspect, named by its source
   * @param plate   plate whose stash keeps the archive
   * @return the archive's file entries
   * @throws IOException when the archive cannot be captured or is refused
   */
  public List<Entry> inspect(@NonNull Describe archive, @NonNull Plate plate) throws IOException {
    return new Inspect(archive.path(), plate.capture(archive), this).inspect(plate);
  }

  /**
   * Describes capturing and inspecting an archive for work that runs later,
   * such as reading a manifest inside a modpack while resolving it: collapsing
   * it inspects as {@link #inspect(Describe, Plate)} does on the collapsing
   * plate.
   *
   * @param archive archive to inspect, named by its source
   * @return unevaluated inspection, producing the archive's file entries
   */
  public Intermediate<List<Entry>> inspect(@NonNull Describe archive) {
    return new Stash.Capture(archive).flatMap(captured -> new Inspect(archive.path(), captured, this));
  }

  /**
   * Explicit central-directory inspection using execution-owned scratch space.
   * It touches only scratch space, so it runs alike while rehearsing and
   * acting.
   *
   * <p>
   * It reports to the plate's observer as it runs: a
   * {@link Reference.Transfer} as the archive is copied into scratch space;
   * then an {@link Observe} for each central-directory entry as it is checked;
   * then the file entries found, or the failure that ended it.
   *
   * @param path    the archive's path within its source, naming it in reports
   * @param archive retained archive, reopened from the plate's stash
   * @param limits  processing bounds
   */
  record Inspect(Address path, Reference.Captured archive, Archive limits)
      implements Intermediate.Step<List<Entry>> {

    @Override
    public String name() {
      return String.format("Inspect %s (%s)", fileName(), Observer.Operand.BYTE.render(archive.size()));
    }

    @Override
    public Scrutiny<List<Entry>> collapse(@NonNull Plate plate) throws IOException {
      return new Scrutiny.Pass<>(inspect(plate));
    }

    List<Entry> inspect(Plate plate) throws IOException {
      try {
        List<Entry> found = scan(plate);
        plate.observer().verified(
            new Observe<>(fileName(), found.size(), found.size(), Observer.Operand.FILE));
        return found;
      } catch (IOException | RuntimeException failure) {
        plate.observer().failed(failure);
        throw failure;
      }
    }

    private String fileName() {
      return path.fileName().orElseThrow();
    }

    private List<Entry> scan(Plate plate) throws IOException {
      if (archive.size() > limits.archiveBytes()) {
        throw new Rejection.Exceeded("ZIP byte limit exceeded.");
      }
      Describe source = plate.retained(archive);
      Node.File copied = limits.copy(source, plate);
      try {
        try (ZipFile zip = new ZipFile(copied.path().toFile())) {
          if (zip.size() > limits.entries()) {
            throw new Rejection.Exceeded("ZIP entry limit exceeded.");
          }
          Map<List<String>, Boolean> paths = new HashMap<>();
          List<ZipEntry> files = new ArrayList<>();
          Map<String, Long> sizes = new HashMap<>();
          long expanded = 0;
          int checked = 0;
          var iterator = zip.entries();
          while (iterator.hasMoreElements()) {
            ZipEntry entry = iterator.nextElement();
            List<String> path = resolve(entry.getName());
            if (paths.putIfAbsent(path, entry.isDirectory()) != null)
              throw new Rejection.Malformed("Duplicate ZIP destination: " + path, null);
            if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED)
              throw new Rejection.Unsupported("Unsupported ZIP compression: " + entry.getName());
            if (entry.getSize() < 0 || entry.getSize() > limits.expandedBytes() - expanded)
              throw new Rejection.Exceeded("ZIP expanded-byte limit exceeded.");
            expanded += entry.getSize();
            sizes.put(entry.getName(), entry.getSize());
            if (entry.isDirectory()) {
              if (entry.getSize() != 0 || entry.getCrc() != 0)
                throw new Rejection.Malformed("Invalid ZIP directory entry.", null);
            } else {
              files.add(entry);
            }
            plate.observer().transfer(
                new Observe<>(fileName(), ++checked, zip.size(), Observer.Operand.ENTRY));
          }
          for (List<String> path : paths.keySet()) {
            for (int depth = path.size() - 1; depth > 0; depth--) {
              List<String> parent = path.subList(0, depth);
              if (Boolean.FALSE.equals(paths.get(parent))) {
                throw new Rejection.Malformed(
                    "ZIP file is also used as a directory: " + String.join("/", parent), null);
              }
            }
          }
          if (files.isEmpty())
            throw new Rejection.Malformed("ZIP contains no files.", null);
          Map<String, Long> catalog = Map.copyOf(sizes);
          List<Entry> result = new ArrayList<>();
          for (ZipEntry entry : files) {
            result.add(new Entry(source, Address.parse(entry.getName()), entry.getSize(), entry.getCrc(), catalog));
          }
          return List.copyOf(result);
        } catch (ZipException | IllegalArgumentException failure) {
          throw new Rejection.Malformed("Invalid ZIP content.", failure);
        }
      } finally {
        try (Lease.Write scratch = copied.write()) {
          scratch.delete();
        }
      }
    }
  }

  /**
   * Extraction effect over every inspected file entry, producing the complete
   * immutable image set. It reads the archive once into execution-owned
   * scratch space and captures each file entry into the plate's stash through
   * a repeatable description of that copy, checking it against the inspected
   * catalog and each entry's size and CRC. A capture that fails throws.
   *
   * <p>
   * It reports to the plate's observer as it runs: a
   * {@link Reference.Transfer} as the archive is copied into scratch space, and
   * as each entry's capture reads it, when the stash reports them; an
   * {@link Observe} for each file once its capture completes; then the files
   * unpacked, the model's violation when it does not allow a file where the
   * archive puts it, or the failure that ended it.
   *
   * @param directory    directory of a model to unpack beneath
   * @param destinations addresses of the extraction directories relative to
   *                     it, each receiving every file
   * @param entries      inspected file entries of one archive, at least one
   * @param limits       processing bounds
   */
  record Unpack(Layout.Directory directory, Set<Address> destinations, List<Entry> entries, Archive limits)
      implements Intermediate.Step<Set<Image<?>>> {

    /**
     * Copies the destinations.
     */
    Unpack {
      destinations = Set.copyOf(destinations);
    }

    /**
     * Describes extraction into one destination.
     */
    Unpack(Layout.Directory directory, Address destination, List<Entry> entries, Archive limits) {
      this(directory, Set.of(destination), entries, limits);
    }

    @Override
    public String name() {
      return String.format("Unpack %s from %s into %s", Observer.Operand.FILE.render(entries.size()),
          entries.getFirst().archive().fileName(), String.join(", ", destinations.stream()
              .map(destination -> directory.address().resolve(destination).canonical())
              .sorted()
              .toList()));
    }

    @Override
    public Scrutiny<Set<Image<?>>> collapse(@NonNull Plate plate) throws IOException {
      try {
        return new Scrutiny.Pass<>(unpack(plate));
      } catch (Violation violation) {
        // Unpacking already reported it to the observer
        return new Scrutiny.Fail<>(null, violation);
      }
    }

    Set<Image<?>> unpack(Plate plate) throws IOException {
      try {
        Node.File copied = limits.copy(entries.getFirst().archive(), plate);
        try (ZipFile zip = new ZipFile(copied.path().toFile())) {
          Set<Image<?>> unpacked = validated(extract(zip, plate));
          plate.observer().verified(files(entries.size()));
          return unpacked;
        } catch (ZipException failure) {
          throw new Rejection.Malformed("Invalid ZIP content.", failure);
        } finally {
          try (Lease.Write scratch = copied.write()) {
            scratch.delete();
          }
        }
      } catch (IOException | RuntimeException failure) {
        plate.observer().failed(failure);
        throw failure;
      }
    }

    private Observe<Integer> files(int completed) {
      return new Observe<>(entries.getFirst().archive().fileName(), completed, entries.size(),
          Observer.Operand.FILE);
    }

    private List<Image<?>> extract(ZipFile zip, Plate plate) throws IOException {
      if (zip.size() != entries.getFirst().catalog().size()) {
        throw new Rejection.Malformed("ZIP entries differ from the inspected catalog.", null);
      }
      List<Image<?>> images = new ArrayList<>(entries.size() * destinations.size());
      int extracted = 0;
      for (Entry entry : entries) {
        ZipEntry found = zip.getEntry(entry.path().canonical());
        if (found == null || found.isDirectory() || found.getSize() != entry.size()
            || found.getCrc() != entry.crc()) {
          throw new Rejection.Malformed("ZIP entries differ from the inspected catalog.", null);
        }
        Describe retained = new Describe() {
          @Override
          public Address path() {
            return entry.path();
          }

          @Override
          public Reference.Pending of() {
            return entry.of();
          }

          @Override
          public InputStream open() throws IOException {
            return entry.entryStream(zip.getInputStream(found));
          }
        };
        Observer<Reference.Captured, Reference.Transfer> read = new Observer<>() {

          @Override
          public void transfer(Reference.Transfer transfer) {
            plate.observer().transfer(transfer);
          }
        };
        Reference.Captured captured = plate.capture(retained, read);
        for (Address destination : destinations) {
          images.add(directory.create(destination.resolve(entry.path()), captured));
        }
        plate.observer().transfer(files(++extracted));
      }
      return images;
    }

    private static Set<Image<?>> validated(List<Image<?>> images) {
      Image.validate(images);
      return Set.copyOf(images);
    }
  }

  /**
   * Immutable description of one file entry from an inspected archive. No open
   * ZIP handle survives inspection; each {@link #open()} rescans the parent
   * archive, failing when its local entries differ from the inspected catalog,
   * and checks the entry's size and CRC when the stream reaches EOF. Stored
   * entries that record their sizes after their data cannot be rescanned and
   * fail to open; {@link Archive#unpack} still extracts them.
   *
   * @param archive parent archive description
   * @param path    entry's path within the archive
   * @param size    exact expanded size
   * @param crc     expected CRC32
   * @param catalog validated central-directory names and expanded sizes
   */
  public record Entry(@NonNull Describe archive, @NonNull Address path, long size, long crc,
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
            return entryStream(zip);
          }
          long skipped = bound(zip, expectedSize).transferTo(OutputStream.nullOutputStream());
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

    private InputStream entryStream(InputStream source) {
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
   * Copies an archive into a new file in the plate's scratch space within the
   * compressed byte limit, reporting each read as a transfer of the archive.
   */
  private Node.File copy(Describe archive, Plate plate) throws IOException {
    Address name = Address.of("tabbie-archive-%s.zip".formatted(UUID.randomUUID()));
    try (Lease.Create scratch = plate.scratch().create(name)) {
      return scratch.create(bounded(archive, plate));
    }
  }

  /**
   * Describes an archive read within the compressed byte limit, reporting each
   * read to the plate's observer as a transfer of the archive.
   */
  private Describe bounded(Describe archive, Plate plate) {
    Reference.Pending pending = archive.of();
    OptionalLong total = pending.expectedSize() != null
        ? OptionalLong.of(pending.expectedSize())
        : OptionalLong.empty();
    return new Describe() {

      @Override
      public Address path() {
        return archive.path();
      }

      @Override
      public Reference.Pending of() {
        return pending;
      }

      @Override
      public InputStream open() throws IOException {
        return new FilterInputStream(bound(archive.open(), archiveBytes)) {
          private long copied = 0;

          @Override
          public int read() throws IOException {
            int value = in.read();
            if (value != -1) {
              report(1);
            }
            return value;
          }

          @Override
          public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = in.read(buffer, offset, length);
            if (read > 0) {
              report(read);
            }
            return read;
          }

          private void report(int read) {
            copied += read;
            plate.observer().transfer(new Reference.Transfer(archive.fileName(), copied, total));
          }
        };
      }
    };
  }

  private static List<String> resolve(String name) throws IOException {
    String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    try {
      List<String> components = Address.parse(value).components();
      if (components.isEmpty()) {
        throw new IllegalArgumentException("Empty ZIP path.");
      }
      return components;
    } catch (IllegalArgumentException failure) {
      throw new Rejection.Unsafe("Unsafe ZIP path: " + name, failure);
    }
  }

  static InputStream bound(InputStream source, long limit) {
    return new FilterInputStream(source) {
      private long remaining = limit;

      @Override
      public int read() throws IOException {
        int value = in.read();
        if (value != -1 && remaining-- <= 0) {
          throw new Rejection.Exceeded("ZIP byte limit exceeded.");
        }
        return value;
      }

      @Override
      public int read(byte[] bytes, int offset, int length) throws IOException {
        if (length == 0)
          return 0;
        if (remaining == 0) {
          int value = read();
          return value == -1 ? -1 : 1;
        }
        int count = in.read(bytes, offset, (int) Math.min(length, remaining));
        if (count > 0) {
          remaining -= count;
        }
        return count;
      }

      @Override
      public long skip(long count) throws IOException {
        long skipped = 0;
        byte[] bytes = new byte[8192];
        while (skipped < count) {
          int read = read(bytes, 0, (int) Math.min(bytes.length, count - skipped));
          if (read < 0) {
            break;
          }
          skipped += read;
        }
        return skipped;
      }
    };
  }

  /**
   * Progress or outcome of an archive step, as reported to its plate's
   * observer: a count of entries or files completed out of a known total.
   * Bytes are reported as {@link Reference.Transfer transfers} instead.
   * Progress and outcome share this shape; an outcome has completed its total.
   *
   * @param <Count>   counted amount type
   * @param subject   what the step is working through, such as an archive's
   *                  file name or an entry's path
   * @param completed amount completed so far, possibly beyond a total the
   *                  archive misdeclared
   * @param total     amount the step works through in all
   * @param unit      unit both amounts are counted in
   */
  public record Observe<Count extends Number>(
      @NonNull String subject,
      @NonNull Count completed,
      @NonNull Count total,
      @NonNull Observer.Operand unit) implements Observer.Renderable {

    /**
     * Checks non-negative amounts.
     *
     * @throws IllegalArgumentException when either amount is negative
     */
    public Observe {
      if (completed.longValue() < 0 || total.longValue() < 0) {
        throw new IllegalArgumentException("Observed amounts must be non-negative.");
      }
    }

    @Override
    public String render() {
      long done = completed.longValue();
      long whole = total.longValue();
      return done == whole
          ? String.format("%s  %s", subject, unit.render(done))
          : String.format(Locale.ROOT, "%s  %s of %s (%d%%)", subject, unit.render(done), unit.render(whole),
              whole == 0 ? 100 : done * 100 / whole);
    }
  }

  /**
   * Thrown when an archive is refused rather than unreadable: it breaks the
   * limits it is processed within, names an unsafe path, needs a feature that
   * is not supported, or is malformed. The same bytes are refused again, so a
   * rejection is reported rather than retried, unlike other I/O failures.
   */
  public abstract static sealed class Rejection extends IOException {

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
     * An entry needs a feature that is not supported, such as its
     * compression method.
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
}
