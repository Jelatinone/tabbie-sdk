package cat.tabbie.sdk.album.repository;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * Bounds ZIP processing. Default limits permit 100,000 entries, 1 GiB
 * compressed
 * and 4 GiB expanded. Description performs no I/O; scratch files exist only
 * while
 * an explicit inspection effect executes and are never installation files.
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
   * data, exceeded bounds, and archives without files. Empty files are
   * preserved; empty directories and filesystem metadata are not installed.
   *
   * Streams opened during execution are closed; the store stays caller-owned.
   * Entry descriptions reopen the retained archive independently, without
   * holding a ZIP handle open across entries.
   *
   * @param <S>         a store that both reads its own bytes and extracts
   *                    described children
   * @param store       bound archive source, also usable as a retention
   *                    backend for its own entries
   * @param destination logical extraction directory
   * @return unevaluated image preparation
   */
  public <S extends Store & Extract> Intermediate<Set<Image<?>>> unpack(@NonNull S store,
      @NonNull Relative destination) {
    return inspect(store).flatMap(found -> new Unpack<>(store, destination, found));
  }

  /**
   * Describes explicit central-directory inspection using execution-owned
   * scratch space, independent of extraction.
   *
   * @param store bound archive source
   * @return unevaluated inspection, producing the archive's file entries
   */
  public <S extends Store> Intermediate<List<Entry>> inspect(@NonNull S store) {
    return new Inspect(store, this);
  }

  /**
   * Explicit central-directory inspection using execution-owned scratch space.
   *
   * @param store  retained archive
   * @param limits processing bounds
   */
  record Inspect(Store store, Archive limits) implements Intermediate.Step<List<Entry>> {
    @Override
    public List<Entry> collapse(Intermediate.Context context) throws IOException {
      Reference.Pending pending = store.of();
      if (pending.expectedSize() != null && pending.expectedSize() > limits.archiveBytes()) {
        throw new IOException("ZIP byte limit exceeded.");
      }
      Path scratch = Files.createTempFile(context.scratch(), "tabbie-archive-", ".zip");
      try {
        try (InputStream input = store.open(); OutputStream output = Files.newOutputStream(scratch)) {
          bound(input, limits.archiveBytes()).transferTo(output);
        }
        try (ZipFile zip = new ZipFile(scratch.toFile())) {
          if (zip.size() > limits.entries()) {
            throw new IOException("ZIP entry limit exceeded.");
          }
          Map<Path, Boolean> paths = new HashMap<>();
          List<ZipEntry> files = new ArrayList<>();
          Map<String, Long> sizes = new HashMap<>();
          long expanded = 0;
          var iterator = zip.entries();
          while (iterator.hasMoreElements()) {
            ZipEntry entry = iterator.nextElement();
            Path path = resolve(entry.getName());
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
          }
          for (Path path : paths.keySet()) {
            for (Path parent = path.getParent(); parent != null; parent = parent.getParent()) {
              if (!paths.get(parent)) {
                throw new IOException("ZIP file is also used as a directory: " + parent);
              }
            }
          }
          if (files.isEmpty())
            throw new IOException("ZIP contains no files.");
          Map<String, Long> catalog = Map.copyOf(sizes);
          List<Entry> result = new ArrayList<>();
          for (ZipEntry entry : files) {
            result.add(new Entry(store, entry.getName(), entry.getSize(), entry.getCrc(), catalog));
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
   * immutable image set.
   *
   * @param <S>         a store that both reads its own bytes and extracts
   *                    described children
   * @param store       retention backend for the archive's entries
   * @param destination logical extraction directory
   * @param entries     inspected file entries
   */
  record Unpack<S extends Store & Extract>(S store, Relative destination, List<Entry> entries)
      implements Intermediate.Step<Set<Image<?>>> {
    @Override
    public Set<Image<?>> collapse(Intermediate.Context context) throws IOException {
      Set<Image<?>> images = new HashSet<>();
      for (Entry entry : entries()) {
        Reference.Captured captured = store.capture(entry)
            .collapse(context);
        images.add(Image.create(destination.resolve(decode(entry.path())), captured));
      }
      Image.validate(images);
      return images;
    }
  }

  /**
   * Immutable description of one entry from an inspected archive. No open ZIP
   * handle survives inspection; reopening scans the retained parent, with
   * entry size and CRC checked at EOF.
   *
   * @param store   bound parent store
   * @param path    canonical entry path
   * @param size    exact expanded size
   * @param crc     expected CRC32
   * @param catalog validated central-directory names and expanded sizes
   */
  record Entry(@NonNull Store store, @NonNull String path, long size, long crc, @NonNull Map<String, Long> catalog)
      implements Describe {
    /**
     * Checks portable entry coordinates and integrity evidence.
     */
    public Entry {
      decode(path);
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
      Path decoded = decode(path);
      return new Reference.Pending(decoded.getFileName().toString(), decoded, null, size);
    }

    @Override
    public InputStream open() throws IOException {
      ZipInputStream zip = new ZipInputStream(store.open());
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

    private InputStream entryStream(ZipInputStream zip) {
      return new FilterInputStream(zip) {
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

        @SuppressWarnings("unused")
        @Override
        public void close() throws IOException {
          try (InputStream input = in) {
            if (!finished && !failed) {
              transferTo(OutputStream.nullOutputStream());
            }
          }
        }
      };
    }
  }

  private static Path resolve(String name) throws IOException {
    String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    try {
      return decode(value);
    } catch (IllegalArgumentException failure) {
      throw new IOException("Unsafe ZIP path: " + name, failure);
    }
  }

  /**
   * Decodes a canonical forward-slash file path, rejecting traversal and native
   * separators. The same encoding can be used on Windows and Unix.
   *
   * @param encoded persisted file destination
   * @return platform path with identical logical components
   */
  static Path decode(@NonNull String encoded) {
    String[] components = encoded.split("/", -1);
    for (String component : components) {
      Reference.validateFilename(component);
    }
    return Path.of(components[0], Arrays.copyOfRange(components, 1, components.length));
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
}
