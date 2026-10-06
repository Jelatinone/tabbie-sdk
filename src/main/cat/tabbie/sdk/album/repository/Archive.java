package cat.tabbie.sdk.album.repository;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * compressed and 4 GiB expanded. Description performs no I/O; scratch files
 * exist only while an explicit inspection effect executes and are never
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
	 * retention backend stay caller-owned. Entry descriptions reopen the archive
	 * independently, without holding a ZIP handle open across entries, so the
	 * archive should describe already retained bytes rather than a remote source.
	 *
	 * @param archive     retained archive, typically
	 *                    {@link Extract#retained(Reference.Captured)}
	 * @param into        retention backend for the extracted entries
	 * @param destination logical extraction directory
	 * @return unevaluated image preparation producing an immutable set
	 */
	public Intermediate<Set<Image<?>>> unpack(@NonNull Describe archive, @NonNull Extract into,
			@NonNull Relative destination) {
		return inspect(archive).flatMap(found -> new Unpack(into, destination, found));
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
	 *
	 * @param archive archive description
	 * @param limits  processing bounds
	 */
	record Inspect(Describe archive, Archive limits) implements Intermediate.Step<List<Entry>> {
		@Override
		public List<Entry> collapse(Step.Context context) throws IOException {
			Reference.Pending pending = archive.of();
			if (pending.expectedSize() != null && pending.expectedSize() > limits.archiveBytes()) {
				throw new IOException("ZIP byte limit exceeded.");
			}
			Path scratch = Files.createTempFile(context.scratch(), "tabbie-archive-", ".zip");
			try {
				try (InputStream input = archive.open(); OutputStream output = Files.newOutputStream(scratch)) {
					bound(input, limits.archiveBytes()).transferTo(output);
				}
				try (ZipFile zip = new ZipFile(scratch.toFile())) {
					if (zip.size() > limits.entries()) {
						throw new IOException("ZIP entry limit exceeded.");
					}
					Map<List<String>, Boolean> paths = new HashMap<>();
					List<ZipEntry> files = new ArrayList<>();
					Map<String, Long> sizes = new HashMap<>();
					long expanded = 0;
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
	 * immutable image set.
	 *
	 * @param into        retention backend for the archive's entries
	 * @param destination logical extraction directory
	 * @param entries     inspected file entries
	 */
	record Unpack(Extract into, Relative destination, List<Entry> entries)
			implements Intermediate.Step<Set<Image<?>>> {
		@Override
		public Set<Image<?>> collapse(Step.Context context) throws IOException {
			List<Image<?>> images = new ArrayList<>();
			for (Entry entry : entries()) {
				Reference.Captured captured = into.capture(entry)
						.collapse(context);
				images.add(Image.create(destination.resolve(Relative.decode(entry.path())), captured));
			}
			Image.validate(images);
			return Set.copyOf(images);
		}
	}

	/**
	 * Immutable description of one file entry from an inspected archive. No open
	 * ZIP handle survives inspection; each {@link #open()} rescans the parent
	 * archive, failing when its local entries differ from the inspected catalog,
	 * and checks the entry's size and CRC when the stream reaches EOF.
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
}