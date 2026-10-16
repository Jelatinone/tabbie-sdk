package cat.tabbie.sdk.album.repository;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.album.revision.Scrutiny;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Relative;
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
   * Streams opened during execution are closed; the archive description and
   * retention backend stay caller-owned. The archive is read twice, once to
   * inspect it and once to capture its file entries, so it should describe
   * already retained bytes rather than a remote source.
   *
   * @param archive     retained archive, typically
   *                    {@link Extract#retained(Reference.Captured)}
   * @param into        retention backend for the extracted entries
   * @param destination logical extraction directory
   * @return unevaluated image preparation producing an immutable set
   */
  public Intermediate<Set<Image<?>>> unpack(@NonNull Describe archive, @NonNull Extract into,
      @NonNull Relative destination) {
    return inspect(archive).flatMap(found -> new Unpack(into, destination, found, this));
  }

  /**
   * Describes explicit central-directory inspection using execution-owned
   * scratch space, independent of extraction.
   *
   * @param archive archive description
   * @return unevaluated inspection, producing the archive's file entries
   */
  public Intermediate<List<Entry>> inspect(@NonNull Describe archive) {
    return new Inspect(archive, this);
  }

  /**
   * Explicit central-directory inspection using execution-owned scratch space.
   * Scrutinizing it performs the inspection, since it only uses scratch space.
   *
   * <p>
   * It reports {@link Observe} records to the context's observer as it runs:
   * the bytes copied into scratch space, when the archive declares its size;
   * then each central-directory entry as it is checked; then the file entries
   * found, or the failure that ended it.
   *
   * @param archive archive description
   * @param limits  processing bounds
   */
  record Inspect(Describe archive, Archive limits) implements Intermediate.Step<List<Entry>> {

    @Override
    public String name() {
      Reference.Pending pending = archive.of();
      return pending.expectedSize() == null
          ? String.format("Inspect %s", pending.fileName())
          : String.format("Inspect %s (%s)", pending.fileName(), Operand.BYTE.render(pending.expectedSize()));
    }

    @Override
    public List<Entry> collapse(Step.Context context) throws IOException {
      long started = System.nanoTime();
      try {
        List<Entry> found = scan(context, started);
        context.observer().verified(
            new Observe<>(archive.of().fileName(), found.size(), found.size(), Operand.FILE, since(started)));
        return found;
      } catch (IOException | RuntimeException failure) {
        context.observer().failed(failure);
        throw failure;
      }
    }

    @Override
    public Scrutiny<List<Entry>> scrutinize(Step.Context context) throws IOException {
      return new Scrutiny.Pass<>(collapse(context));
    }

    private List<Entry> scan(Step.Context context, long started) throws IOException {
      Reference.Pending pending = archive.of();
      if (pending.expectedSize() != null && pending.expectedSize() > limits.archiveBytes()) {
        throw new IOException("ZIP byte limit exceeded.");
      }
      Path scratch = Files.createTempFile(context.scratch(), "tabbie-archive-", ".zip");
      try {
        limits.copy(archive, scratch, context, started);
        try (ZipFile zip = new ZipFile(scratch.toFile())) {
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
            context.observer().transfer(
                new Observe<>(pending.fileName(), ++checked, zip.size(), Operand.ENTRY, since(started)));
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
            result.add(new Entry(archive, entry.getName(), entry.getSize(), entry.getCrc(), catalog));
          }
          return List.copyOf(result);
        } catch (IllegalArgumentException failure) {
          throw new IOException("Invalid ZIP content.", failure);
        }
      } finally {
        Files.deleteIfExists(scratch);
      }
    }
  }

  /**
   * Extraction effect over every inspected file entry, producing the complete
   * immutable image set. It reads the archive once into execution-owned
   * scratch space and captures each file entry through a repeatable
   * description of that copy, checking it against the inspected catalog and
   * each entry's size and CRC. Scrutinizing it scrutinizes each capture instead,
   * failing when any capture fails and mocking when any capture mocks.
   *
   * <p>
   * It reports {@link Observe} records to the context's observer as it runs:
   * the bytes copied into scratch space, when the archive declares its size;
   * the bytes of each entry as its capture reads them, when the backend
   * reports them; each file once its capture completes; then the files
   * unpacked, or the failure that ended it. A scrutiny that fails or mocks
   * reports no outcome.
   *
   * @param into        retention backend for the archive's entries
   * @param destination logical extraction directory
   * @param entries     inspected file entries of one archive, at least one
   * @param limits      processing bounds
   */
  record Unpack(Extract into, Relative destination, List<Entry> entries, Archive limits)
      implements Intermediate.Step<Set<Image<?>>> {

    @Override
    public String name() {
      // The encoding leaves out the kind of mount, so name it
      String mount = switch (destination) {
        case Relative.Root _ -> "root";
        case Relative.World _ -> "world";
      };
      return String.format("Unpack %s from %s into %s:/%s", Operand.FILE.render(entries.size()),
          entries.getFirst().archive().of().fileName(), mount, destination.encoded());
    }

    @Override
    public Set<Image<?>> collapse(Step.Context context) throws IOException {
      long started = System.nanoTime();
      try {
        Path scratch = Files.createTempFile(context.scratch(), "tabbie-archive-", ".zip");
        try (ZipFile zip = open(scratch, context, started)) {
          List<Image<?>> images = new ArrayList<>(entries.size());
          for (Intermediate<Image<?>> capture : extract(zip, context, started)) {
            images.add(capture.collapse(context));
            context.observer().transfer(files(images.size(), started));
          }
          Set<Image<?>> unpacked = validated(images);
          context.observer().verified(files(unpacked.size(), started));
          return unpacked;
        } finally {
          Files.deleteIfExists(scratch);
        }
      } catch (IOException | RuntimeException failure) {
        context.observer().failed(failure);
        throw failure;
      }
    }

    @Override
    public Scrutiny<Set<Image<?>>> scrutinize(Step.Context context) throws IOException {
      long started = System.nanoTime();
      try {
        Path scratch = Files.createTempFile(context.scratch(), "tabbie-archive-", ".zip");
        try (ZipFile zip = open(scratch, context, started)) {
          List<Image<?>> images = new ArrayList<>(entries.size());
          boolean mocked = false;
          int scrutinized = 0;
          for (Intermediate<Image<?>> capture : extract(zip, context, started)) {
            switch (capture.scrutinize(context)) {
              case Scrutiny.Pass<Image<?>> pass -> images.add(pass.result());
              case Scrutiny.Fail<Image<?>> _ -> {
                return new Scrutiny.Fail<>();
              }
              case Scrutiny.Mock<Image<?>> _ -> mocked = true;
            }
            context.observer().transfer(files(++scrutinized, started));
          }
          if (mocked) {
            return new Scrutiny.Mock<>();
          }
          Set<Image<?>> unpacked = validated(images);
          context.observer().verified(files(unpacked.size(), started));
          return new Scrutiny.Pass<>(unpacked);
        } finally {
          Files.deleteIfExists(scratch);
        }
      } catch (IOException | RuntimeException failure) {
        context.observer().failed(failure);
        throw failure;
      }
    }

    private ZipFile open(Path scratch, Step.Context context, long started) throws IOException {
      limits.copy(entries.getFirst().archive(), scratch, context, started);
      return new ZipFile(scratch.toFile());
    }

    private Observe<Integer> files(int completed, long started) {
      return new Observe<>(entries.getFirst().archive().of().fileName(), completed, entries.size(), Operand.FILE,
          since(started));
    }

    private List<Intermediate<Image<?>>> extract(ZipFile zip, Step.Context context, long started)
        throws IOException {
      if (zip.size() != entries.getFirst().catalog().size()) {
        throw new IOException("ZIP entries differ from the inspected catalog.");
      }
      List<Intermediate<Image<?>>> images = new ArrayList<>();
      for (Entry entry : entries) {
        ZipEntry found = zip.getEntry(entry.path());
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
        Observer<Reference.Captured, Extract.Transfer> read = new Observer<>() {

          @Override
          public void transfer(Extract.Transfer transfer) {
            context.observer().transfer(
                new Observe<>(entry.path(), transfer.transferredSize(), entry.size(), Operand.BYTE, since(started)));
          }
        };
        Relative target = destination.resolve(Relative.decode(entry.path()));
        images.add(into.capture(retained, read).map(captured -> Image.create(target, captured)));
      }
      return List.copyOf(images);
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
   * @param path    canonical entry path
   * @param size    exact expanded size
   * @param crc     expected CRC32
   * @param catalog validated central-directory names and expanded sizes
   */
  public record Entry(@NonNull Describe archive, @NonNull String path, long size, long crc,
      @NonNull Map<String, Long> catalog) implements Describe {
    /**
     * Copies the catalog and checks portable entry coordinates and integrity
     * evidence.
     *
     * @throws IllegalArgumentException when the path is not canonical, the
     *                                  entry disagrees with the catalog, or the
     *                                  size or CRC is out of range
     */
    public Entry {
      if (Relative.decode(path).isEmpty()) {
        throw new IllegalArgumentException("A ZIP entry needs a path.");
      }
      catalog = Map.copyOf(catalog);
      if (!Long.valueOf(size).equals(catalog.get(path))) {
        throw new IllegalArgumentException("ZIP entry must agree with its inspected catalog.");
      }
      if (size < 0 || crc < 0 || crc > 0xffffffffL) {
        throw new IllegalArgumentException("Invalid ZIP entry evidence.");
      }
    }

    @Override
    public Reference.Pending of() {
      return new Reference.Pending(Relative.decode(path).getLast(), path, null, size);
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
          if (current.getName().equals(path)) {
            return entryStream(zip);
          }
          long skipped = bound(zip, expectedSize).transferTo(OutputStream.nullOutputStream());
          if (skipped != expectedSize) {
            throw new IOException("ZIP skipped entry size differs from the inspected catalog.");
          }
        }
        throw new IOException("Retained ZIP entry is missing: " + path);
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
              throw new IOException("ZIP entry size or CRC mismatch: " + path);
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
   * Copies an archive into scratch space within the compressed byte limit,
   * reporting the bytes copied so far when the archive declares its size.
   */
  private void copy(Describe archive, Path scratch, Intermediate.Step.Context context, long started)
      throws IOException {
    Reference.Pending pending = archive.of();
    try (InputStream input = archive.open(); OutputStream output = Files.newOutputStream(scratch)) {
      InputStream bounded = bound(input, archiveBytes);
      byte[] buffer = new byte[8192];
      long copied = 0;
      for (int read; (read = bounded.read(buffer)) != -1;) {
        output.write(buffer, 0, read);
        copied += read;
        if (pending.expectedSize() != null) {
          context.observer().transfer(
              new Observe<>(pending.fileName(), copied, pending.expectedSize(), Operand.BYTE, since(started)));
        }
      }
    }
  }

  private static Duration since(long started) {
    return Duration.ofNanos(System.nanoTime() - started);
  }

  private static List<String> resolve(String name) throws IOException {
    String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    try {
      List<String> components = Relative.decode(value);
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
   * Progress or outcome of an archive step, as reported to its context's
   * observer: an amount completed out of a known total, and the time the step
   * has taken so far. Steps count entries and files as {@link Integer} and
   * bytes as {@link Long}. Progress and outcome share this shape; an outcome
   * has completed its total.
   *
   * @param <Count>   counted amount type
   * @param subject   what the step is working through, such as an archive's
   *                  file name or an entry's path
   * @param completed amount completed so far, possibly beyond a total the
   *                  archive misdeclared
   * @param total     amount the step works through in all
   * @param unit      unit both amounts are counted in
   * @param duration  time elapsed since the step began
   */
  public record Observe<Count extends Number>(
      @NonNull String subject,
      @NonNull Count completed,
      @NonNull Count total,
      @NonNull Operand unit,
      @NonNull Duration duration) implements Observer.Renderable {

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
   * Unit an archive step counts its progress in.
   */
  public enum Operand {

    /**
     * Bytes, in the largest binary unit that keeps the count at least one,
     * such as {@code 3.2 MiB}.
     */
    BYTE {
      @Override
      public String render(long count) {
        if (count < 1024) {
          return count + " B";
        }
        int exponent = (63 - Long.numberOfLeadingZeros(count)) / 10;
        return String.format(Locale.ROOT, "%.1f %ciB", count / Math.pow(1024, exponent), "KMGTPE".charAt(exponent - 1));
      }
    },

    /**
     * Central-directory entries, files and directories alike.
     */
    ENTRY {
      @Override
      public String render(long count) {
        return String.format(Locale.ROOT, count == 1 ? "%d entry" : "%d entries", count);
      }
    },

    /**
     * Files.
     */
    FILE {
      @Override
      public String render(long count) {
        return String.format(Locale.ROOT, count == 1 ? "%d file" : "%d files", count);
      }
    };

    /**
     * Renders a count in this unit.
     *
     * @param count count to render
     * @return human-readable count
     */
    public abstract String render(long count);
  }
}
