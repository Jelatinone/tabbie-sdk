package cat.tabbie.sdk.album;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import lombok.NonNull;

/**
 * Bounds ZIP processing; defaults allow 100,000 entries, 1 GiB compressed, and
 * 4 GiB expanded. Callers can supply tighter or larger bounds explicitly.
 *
 * @param entries       maximum directory and file entries
 * @param archiveBytes  maximum input ZIP bytes
 * @param expandedBytes maximum total uncompressed bytes
 */
public record Archive(int entries, long archiveBytes, long expandedBytes) {

  /**
   * Default limits for entry count, compressed input, and expanded bytes.
   */
  public static final Archive DEFAULT = new Archive(100_000, 1L << 30, 4L << 30);

  /**
   * Checks positive processing bounds
   */
  public Archive {
    if (entries < 1 || archiveBytes < 1 || expandedBytes < 1) {
      throw new IllegalArgumentException("ZIP limits must be positive.");
    }
  }

  /**
   * Captures stored/deflated ZIP entries, including ZIP64, beneath a logical
   * relative directory. No installation files are written. A temporary archive
   * allows central-directory validation; entry sizes and CRCs are checked.
   * Traversal, duplicates, file/directory collisions, unsupported compression or
   * encryption, corrupt data, and exceeded bounds are rejected. Every file is
   * ordinary bytes; filesystem links, permissions, and timestamps are not
   * applied.
   * Empty files are preserved, empty directories omitted, and archives with no
   * files rejected. Failure returns no images; complete retained blobs follow the
   * store's lifetime policy. Caller-owned streams and stores remain open.
   *
   * @param stream      ZIP content
   * @param destination logical extraction root
   * @param store       retention store
   * @param limits      processing bounds
   * @return complete immutable file images
   * @throws IOException when input, validation, or capture fails
   */
  public static Set<Image<?>> unpack(
      @NonNull InputStream stream,
      @NonNull Path destination,

      @NonNull Store store,

      @NonNull Archive limits) throws IOException {
    Path root = Image.relative(destination);
    Path temporary = Files.createTempFile("tabbie-artifact-", ".zip");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        bound(stream, limits.archiveBytes()).transferTo(output);
      }
      try (ZipFile zip = new ZipFile(temporary.toFile())) {
        if (zip.size() > limits.entries()) {
          throw new IOException("ZIP entry limit exceeded.");
        }
        Map<Path, Boolean> paths = new HashMap<>();
        Map<Path, ZipEntry> files = new LinkedHashMap<>();

        long expanded = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          Path path = resolve(entry.getName());
          if (paths.putIfAbsent(path, entry.isDirectory()) != null) {
            throw new IOException("Duplicate ZIP destination: " + path);
          }
          if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
            throw new IOException("Unsupported ZIP compression: " + entry.getName());
          }
          if (entry.getSize() < 0 || entry.getSize() > limits.expandedBytes() - expanded) {
            throw new IOException("ZIP expanded-byte limit exceeded.");
          }
          expanded += entry.getSize();
          if (entry.isDirectory()) {
            if (entry.getSize() != 0 || entry.getCrc() != 0)
              throw new IOException("Invalid ZIP directory entry.");
          } else {
            files.put(path, entry);
          }
        }
        for (Path path : paths.keySet()) {
          for (Path parent = path.getParent(); parent != null; parent = parent.getParent()) {
            if (Boolean.FALSE.equals(paths.get(parent))) {
              throw new IOException("ZIP file is also used as a directory: " + parent);
            }
          }
        }
        if (files.isEmpty()) {
          throw new IOException("ZIP contains no files.");
        }
        Set<Image<?>> images = new HashSet<>();
        for (var file : files.entrySet()) {
          ZipEntry entry = file.getValue();
          try (CheckedInputStream input = new CheckedInputStream(zip.getInputStream(entry), new CRC32())) {
            Reference captured = store.capture(bound(input, entry.getSize()));
            if (input.read() != -1 || captured.size() != entry.getSize()
                || input.getChecksum().getValue() != entry.getCrc()) {
              throw new IOException("ZIP entry size or CRC mismatch: " + entry.getName());
            }
            images.add(Image.create(root.resolve(file.getKey()), captured));
          }
        }
        return images;
      } catch (IllegalArgumentException exception) {
        throw new IOException("Invalid ZIP content", exception);
      }
    } finally {
      Files.deleteIfExists(temporary);
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
    for (String component : components)
      Reference.validateFilename(component);
    return Path.of(components[0], Arrays.copyOfRange(components, 1, components.length));
  }

  static InputStream bound(InputStream source, long limit) {
    return new FilterInputStream(source) {
      private long remaining = limit;

      @Override
      public int read() throws IOException {
        int value = in.read();
        if (value != -1 && remaining-- <= 0)
          throw new IOException("ZIP byte limit exceeded.");
        return value;
      }

      @Override
      public int read(byte[] bytes, int offset, int length) throws IOException {
        if (length == 0)
          return 0;
        if (remaining == 0)
          return read();
        int count = in.read(bytes, offset, (int) Math.min(length, remaining));
        if (count > 0)
          remaining -= count;
        return count;
      }

      @Override
      public long skip(long count) throws IOException {
        long skipped = 0;
        byte[] buffer = new byte[8192];
        while (skipped < count) {
          int read = read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
          if (read < 0)
            break;
          skipped += read;
        }
        return skipped;
      }

      @Override
      public void close() {
      }
    };
  }
}
