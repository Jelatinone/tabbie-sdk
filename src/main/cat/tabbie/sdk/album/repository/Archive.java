package cat.tabbie.sdk.album.repository;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import cat.tabbie.sdk.platform.layout.Lease;
import cat.tabbie.sdk.platform.layout.Node;
import lombok.NonNull;

/**
 * Bounds ZIP processing. Default limits permit 100,000 entries, 1 GiB
 * compressed and 4 GiB expanded. Description performs no I/O; scratch files
 * exist only while an inspection or unpacking effect executes and are never
 * installation files.
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
   * Describes inspection of the archive's central directory, followed by
   * capture of every file entry and assembly of the complete image set.
   * Supports stored/deflated ZIP and ZIP64. Rejects traversal, duplicates,
   * file/directory collisions, unsupported compression, encryption, corrupt
   * data, exceeded bounds, and archives without files. Parent directories
   * need not be listed. Empty files are preserved; empty directories and
   * filesystem metadata are not installed.
   *
   * <p>
   * The archive is read twice, once to inspect it and once to capture its file
   * entries, so it must already be retained: both reopen it from the stash of
   * the plate that collapses the result, and its entries are captured into the
   * same stash. Streams opened during execution are closed.
   *
   * @param archive     retained archive
   * @param destination logical extraction directory
   * @return unevaluated image preparation producing an immutable set
   */
  public Intermediate<Set<Image<?>>> unpack(@NonNull Reference.Captured archive, @NonNull Address destination) {
    return inspect(archive).flatMap(found -> new Unpack(destination, found, this));
  }

  /**
   * Describes explicit central-directory inspection using execution-owned
   * scratch space, independent of extraction.
   *
   * @param archive retained archive
   * @return unevaluated inspection, producing the archive's file entries
   */
  public Intermediate<List<Entry>> inspect(@NonNull Reference.Captured archive) {
    return new Inspect(archive, this);
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
   * @param archive retained archive, reopened from the plate's stash
   * @param limits  processing bounds
   */
  record Inspect(Reference.Captured archive, Archive limits) implements Intermediate.Step<List<Entry>> {

    @Override
    public String name() {
      return String.format("Inspect %s (%s)", archive.fileName(), Observer.Operand.BYTE.render(archive.size()));
    }

    @Override
    public Scrutiny<List<Entry>> collapse(@NonNull Plate plate) throws IOException {
      try {
        List<Entry> found = scan(plate);
        plate.observer().verified(
            new Observe<>(archive.fileName(), found.size(), found.size(), Observer.Operand.FILE));
        return new Scrutiny.Pass<>(found);
      } catch (IOException | RuntimeException failure) {
        plate.observer().failed(failure);
        throw failure;
      }
    }

    private List<Entry> scan(Plate plate) throws IOException {
      if (archive.size() > limits.archiveBytes()) {
        throw new IOException("ZIP byte limit exceeded.");
      }
      Describe source = plate.retained(archive);
      Node.File copied = limits.copy(source, plate);
      try {
        try (ZipFile zip = new ZipFile(copied.path().toFile())) {
          if (zip.size() > limits.entries()) {
            throw new IOException("ZIP entry limit exceeded.");
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
              throw new IOException("Duplicate ZIP destination: " + path);
            if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED)
              throw new IOException("Unsupported ZIP compression: " + entry.getName());
            if (entry.getSize() < 0 || entry.getSize() > limits.expandedBytes() - expanded)
              throw new IOException("ZIP expanded-byte limit exceeded.");
            expanded += entry.getSize();
            sizes.put(entry.getName(), entry.getSize());
            if (entry.isDirectory()) {
              if (entry.getSize() != 0 || entry.getCrc() != 0)
                throw new IOException("Invalid ZIP directory entry.");
            } else {
              files.add(entry);
            }
            plate.observer().transfer(
                new Observe<>(archive.fileName(), ++checked, zip.size(), Observer.Operand.ENTRY));
          }
          for (List<String> path : paths.keySet()) {
            for (int depth = path.size() - 1; depth > 0; depth--) {
              List<String> parent = path.subList(0, depth);
              if (Boolean.FALSE.equals(paths.get(parent))) {
                throw new IOException("ZIP file is also used as a directory: " + String.join("/", parent));
              }
            }
          }
          if (files.isEmpty())
            throw new IOException("ZIP contains no files.");
          Map<String, Long> catalog = Map.copyOf(sizes);
          List<Entry> result = new ArrayList<>();
          for (ZipEntry entry : files) {
            result.add(new Entry(source, Address.parse(entry.getName()), entry.getSize(), entry.getCrc(), catalog));
          }
          return List.copyOf(result);
        } catch (IllegalArgumentException failure) {
          throw new IOException("Invalid ZIP content.", failure);
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
   * unpacked, or the failure that ended it.
   *
   * @param destination logical extraction directory
   * @param entries     inspected file entries of one archive, at least one
   * @param limits      processing bounds
   */
  record Unpack(Address destination, List<Entry> entries, Archive limits)
      implements Intermediate.Step<Set<Image<?>>> {

    @Override
    public String name() {
      return String.format("Unpack %s from %s into %s", Observer.Operand.FILE.render(entries.size()),
          entries.getFirst().archive().of().fileName(), destination.canonical());
    }

    @Override
    public Scrutiny<Set<Image<?>>> collapse(@NonNull Plate plate) throws IOException {
      try {
        Node.File copied = limits.copy(entries.getFirst().archive(), plate);
        try (ZipFile zip = new ZipFile(copied.path().toFile())) {
          Set<Image<?>> unpacked = validated(extract(zip, plate));
          plate.observer().verified(files(unpacked.size()));
          return new Scrutiny.Pass<>(unpacked);
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
      return new Observe<>(entries.getFirst().archive().of().fileName(), completed, entries.size(),
          Observer.Operand.FILE);
    }

    private List<Image<?>> extract(ZipFile zip, Plate plate) throws IOException {
      if (zip.size() != entries.getFirst().catalog().size()) {
        throw new IOException("ZIP entries differ from the inspected catalog.");
      }
      List<Image<?>> images = new ArrayList<>(entries.size());
      for (Entry entry : entries) {
        ZipEntry found = zip.getEntry(entry.path().canonical());
        if (found == null || found.isDirectory() || found.getSize() != entry.size()
            || found.getCrc() != entry.crc()) {
          throw new IOException("ZIP entries differ from the inspected catalog.");
        }
        Describe retained = new Describe() {
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
        Address target = destination.resolve(entry.path());
        images.add(Image.create(target, plate.capture(retained, read)));
        plate.observer().transfer(files(images.size()));
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
      return new Reference.Pending(path, null, size);
    }

    @Override
    public InputStream open() throws IOException {
      ZipInputStream zip = new ZipInputStream(archive.open());
      try {
        Set<String> visited = new HashSet<>();
        for (ZipEntry current; (current = zip.getNextEntry()) != null;) {
          Long expectedSize = catalog.get(current.getName());
          if (expectedSize == null || !visited.add(current.getName())) {
            throw new IOException("ZIP local entries differ from the inspected catalog.");
          }
          if (current.getName().equals(path.canonical())) {
            return entryStream(zip);
          }
          long skipped = bound(zip, expectedSize).transferTo(OutputStream.nullOutputStream());
          if (skipped != expectedSize) {
            throw new IOException("ZIP skipped entry size differs from the inspected catalog.");
          }
        }
        throw new IOException("Retained ZIP entry is missing: " + path.canonical());
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
              throw new IOException("ZIP entry size exceeded.");
            }
            checksum.update(bytes, offset, read);
          } else if (read == -1 && !finished) {
            finished = true;
            if (count != size || checksum.getValue() != crc) {
              throw new IOException("ZIP entry size or CRC mismatch: " + path.canonical());
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
            plate.observer().transfer(new Reference.Transfer(pending.fileName(), copied, total));
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
      throw new IOException("Unsafe ZIP path: " + name, failure);
    }
  }

  static InputStream bound(InputStream source, long limit) {
    return new FilterInputStream(source) {
      private long remaining = limit;

      @Override
      public int read() throws IOException {
        int value = in.read();
        if (value != -1 && remaining-- <= 0) {
          throw new IOException("ZIP byte limit exceeded.");
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
}
