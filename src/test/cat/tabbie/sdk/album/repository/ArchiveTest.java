package cat.tabbie.sdk.album.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import cat.tabbie.sdk.TestFixtures.MockRetention;
import cat.tabbie.sdk.TestFixtures.MockSource;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.platform.Relative;

import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.collapse;
import static cat.tabbie.sdk.TestFixtures.entries;
import static cat.tabbie.sdk.TestFixtures.source;
import static cat.tabbie.sdk.TestFixtures.zip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveTest {

	@TempDir
	Path scratch;

	@Test
	void limits_mustBePositive() {
		assertThrows(IllegalArgumentException.class, () -> new Archive(0, 1L, 1L));
		assertThrows(IllegalArgumentException.class, () -> new Archive(1, 0L, 1L));
		assertThrows(IllegalArgumentException.class, () -> new Archive(1, 1L, 0L));
	}

	@Test
	void inspect_listsFileEntries_andLeavesNoScratchFiles() throws IOException {
		MockSource archive = source("pack.zip", zip(entries("assets/", "", "assets/a.txt", "alpha", "b.txt", "")));

		List<Archive.Entry> found = collapse(Archive.DEFAULT.inspect(archive), scratch);

		assertEquals(Set.of("assets/a.txt", "b.txt"), found.stream().map(Archive.Entry::path).collect(Collectors.toSet()));
		assertEquals(5L, found.stream().filter(entry -> entry.path().equals("assets/a.txt")).findFirst().orElseThrow()
				.size());
		try (Stream<Path> remaining = Files.list(scratch)) {
			assertEquals(0L, remaining.count());
		}
	}

	@Test
	void unpack_capturesEveryFile_beneathTheDestination() throws IOException {
		MockRetention retention = new MockRetention();
		MockSource archive = source("pack.zip", zip(entries("data/a.txt", "alpha", "b.txt", "", "data/c/d.txt", "delta")));

		Set<Image<?>> images = collapse(Archive.DEFAULT.unpack(archive, retention, Relative.root("packs", "one")),
				scratch);

		Map<Relative, Image.File> created = images.stream()
				.map(image -> assertInstanceOf(Image.Installation.class, image))
				.collect(Collectors.toMap(image -> image.target().relative(), Image.Installation::after));
		assertEquals(Set.of(
				Relative.root("packs", "one", "data", "a.txt"),
				Relative.root("packs", "one", "b.txt"),
				Relative.root("packs", "one", "data", "c", "d.txt")), created.keySet());
		Reference.Captured alpha = assertInstanceOf(Image.File.Present.class,
				created.get(Relative.root("packs", "one", "data", "a.txt"))).reference();
		Reference.Captured empty = assertInstanceOf(Image.File.Present.class,
				created.get(Relative.root("packs", "one", "b.txt"))).reference();
		assertArrayEquals(bytes("alpha"), retention.read(alpha));
		assertEquals(0L, empty.size());
		assertEquals("a.txt", alpha.fileName());
	}

	@Test
	void unpack_supportsStoredEntries_andWorldScopedDestinations() throws IOException {
		MockRetention retention = new MockRetention();
		MockSource archive = source("pack.zip", zip(entries("pack.mcmeta", "{}"), ZipEntry.STORED));

		Set<Image<?>> images = collapse(Archive.DEFAULT.unpack(archive, retention, Relative.world("datapacks", "x")),
				scratch);

		assertEquals(Set.of(new Image.Target.File(Relative.world("datapacks", "x", "pack.mcmeta"))),
				images.stream().map(Image::target).collect(Collectors.toSet()));
	}

	@Test
	void unpack_describesNothing_untilCollapsed() {
		MockRetention retention = new MockRetention();
		MockSource archive = source("pack.zip", zip(entries("a.txt", "a")));

		Archive.DEFAULT.unpack(archive, retention, Relative.root());

		assertEquals(0, archive.opens());
		assertEquals(0, retention.captures());
	}

	@Test
	void inspect_rejectsUnsafeEntryPaths() {
		for (String unsafe : List.of("../evil.txt", "a/../../evil.txt", "/evil.txt", "a\\evil.txt", "a/./b.txt",
				"a//b.txt", "C:evil.txt")) {
			MockSource archive = source("pack.zip", zip(entries(unsafe, "x")));

			assertThrows(IOException.class, () -> collapse(Archive.DEFAULT.inspect(archive), scratch), unsafe);
		}
	}

	@Test
	void inspect_rejectsDuplicateDestinations_andFilesUsedAsDirectories() {
		MockSource duplicate = source("pack.zip", zip(entries("a", "file", "a/", "")));
		MockSource collision = source("pack.zip", zip(entries("a", "file", "a/b.txt", "nested")));

		assertThrows(IOException.class, () -> collapse(Archive.DEFAULT.inspect(duplicate), scratch));
		assertThrows(IOException.class, () -> collapse(Archive.DEFAULT.inspect(collision), scratch));
	}

	@Test
	void inspect_rejectsArchivesWithoutFiles_andCorruptData() {
		MockSource directoriesOnly = source("pack.zip", zip(entries("a/", "", "a/b/", "")));
		MockSource corrupt = source("pack.zip", bytes("this is not a zip archive"));

		assertThrows(IOException.class, () -> collapse(Archive.DEFAULT.inspect(directoriesOnly), scratch));
		assertThrows(IOException.class, () -> collapse(Archive.DEFAULT.inspect(corrupt), scratch));
	}

	@Test
	void inspect_enforcesEntryArchiveAndExpandedLimits() {
		byte[] content = zip(entries("a.txt", "0123456789", "b.txt", "x"));

		assertThrows(IOException.class,
				() -> collapse(new Archive(1, Long.MAX_VALUE, Long.MAX_VALUE).inspect(source("p.zip", content)), scratch));
		assertThrows(IOException.class,
				() -> collapse(new Archive(10, 16L, Long.MAX_VALUE).inspect(source("p.zip", content)), scratch));
		assertThrows(IOException.class,
				() -> collapse(new Archive(10, Long.MAX_VALUE, 10L).inspect(source("p.zip", content)), scratch));
	}

	@Test
	void inspect_rejectsDeclaredOversizeArchives_withoutOpeningThem() {
		MockSource declared = new MockSource(new Reference.Pending("p.zip", null, 1_000L), zip(entries("a.txt", "a")));

		assertThrows(IOException.class, () -> collapse(new Archive(10, 100L, 100L).inspect(declared), scratch));
		assertEquals(0, declared.opens());
	}

	@Test
	void entry_rejectsArchivesThatChangedSinceInspection() throws IOException {
		Swappable archive = new Swappable(zip(entries("a.txt", "aa", "b.txt", "bb")));
		List<Archive.Entry> found = collapse(Archive.DEFAULT.inspect(archive), scratch);
		Archive.Entry b = found.stream().filter(entry -> entry.path().equals("b.txt")).findFirst().orElseThrow();
		Archive.Entry a = found.stream().filter(entry -> entry.path().equals("a.txt")).findFirst().orElseThrow();

		archive.content = zip(entries("a.txt", "zz", "c.txt", "cc"));

		assertThrows(IOException.class, () -> readAll(b));
		assertThrows(IOException.class, () -> readAll(a));
	}

	@Test
	void entry_reopensIndependently_andVerifiesContentAtEndOfStream() throws IOException {
		MockSource archive = source("pack.zip", zip(entries("a.txt", "alpha", "b.txt", "beta")));
		Archive.Entry b = collapse(Archive.DEFAULT.inspect(archive), scratch).stream()
				.filter(entry -> entry.path().equals("b.txt"))
				.findFirst()
				.orElseThrow();

		byte[] first = readAll(b);
		byte[] second = readAll(b);

		assertArrayEquals(bytes("beta"), first);
		assertArrayEquals(bytes("beta"), second);
		assertEquals(new Reference.Pending("b.txt", "b.txt", null, 4L), b.of());
	}

	@Test
	void entry_rejectsEvidenceThatDisagreesWithItsCatalog() {
		MockSource archive = source("pack.zip", new byte[0]);

		assertThrows(IllegalArgumentException.class, () -> new Archive.Entry(archive, "a.txt", 2L, 0L, Map.of("a.txt", 3L)));
		assertThrows(IllegalArgumentException.class, () -> new Archive.Entry(archive, "a.txt", 3L, -1L, Map.of("a.txt", 3L)));
		assertThrows(IllegalArgumentException.class, () -> new Archive.Entry(archive, "", 0L, 0L, Map.of("", 0L)));
		assertTrue(new Archive.Entry(archive, "a.txt", 3L, 0L, Map.of("a.txt", 3L)).catalog().containsKey("a.txt"));
	}

	private static byte[] readAll(Describe describe) throws IOException {
		try (InputStream input = describe.open()) {
			return input.readAllBytes();
		}
	}

	/**
	 * A retained archive whose bytes can be replaced after inspection.
	 */
	private static final class Swappable implements Describe {

		byte[] content;

		Swappable(byte[] content) {
			this.content = content;
		}

		@Override
		public Reference.Pending of() {
			return new Reference.Pending("pack.zip");
		}

		@Override
		public InputStream open() {
			return new ByteArrayInputStream(content);
		}
	}
}
