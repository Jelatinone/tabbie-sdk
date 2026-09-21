package cat.tabbie.sdk.album;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cat.tabbie.sdk.Fixtures;

class ArchiveTest {
  static Set<Image<?>> unpack(byte[] bytes, Fixtures.Retention store) throws IOException {
    return Archive.unpack(new ByteArrayInputStream(bytes), Path.of("selected"), store, Archive.DEFAULT);
  }

  @SuppressWarnings("resource")
  @Test
  void returnsAllNestedAndEmptyFilesWithEntryNamesAndRetainedBytes() throws IOException {
    Fixtures.Source source = new Fixtures.Source(Fixtures.zip("folder/", "", "folder/data.json", "{}", "empty", ""));
    Fixtures.Retention store = new Fixtures.Retention();
    try (InputStream input = source.open()) {
      Set<Image<?>> images = Archive.unpack(input, Path.of("chosen/root"), store, Archive.DEFAULT);
      assertEquals(Set.of(Path.of("chosen/root/folder/data.json"), Path.of("chosen/root/empty")),
          images.stream().map(Image::path).collect(java.util.stream.Collectors.toSet()));
      for (Image<?> image : images) {
        Reference reference = ((Image.File.Present) image.after()).reference();
        assertEquals(image.path().getFileName().toString(), reference.fileName());
        try (InputStream retained = store.open(reference)) {
          assertArrayEquals(reference.fileName().equals("empty") ? new byte[0] : "{}".getBytes(),
              retained.readAllBytes());
        }
      }
      assertThrows(UnsupportedOperationException.class, images::clear);
      assertEquals(0, source.streamCloses);
    }
    assertEquals(1, source.streamCloses);
    assertEquals(0, store.storeCloses);
    assertEquals(0, store.opens);
  }

  @ParameterizedTest
  @ValueSource(strings = { "../outside", "a/../../outside", "/absolute", "C:/drive", "C:relative", "back\\slash",
      "a//b", "./dot", "a/../b", "bad:stream", "//server/share", "a/./b" })
  void rejectsUnsafePathsBeforeCapturing(String name) throws IOException {
    Fixtures.Retention store = new Fixtures.Retention();
    assertThrows(IOException.class, () -> unpack(Fixtures.zip(name, "payload"), store));
    assertEquals(0, store.captures);
  }

  @Test
  void rejectsDuplicatesAndFileDirectoryCollisionsInEitherOrder() throws IOException {
    byte[] duplicate = Fixtures.zip("a", "first", "b", "second");
    for (int i = 0; i < duplicate.length - 4; i++) {
      if (signature(duplicate, i, 0x04034b50))
        duplicate[i + 30] = 'a';
      if (signature(duplicate, i, 0x02014b50))
        duplicate[i + 46] = 'a';
    }
    for (byte[] bytes : new byte[][] { duplicate, Fixtures.zip("a", "file", "a/b", "child"),
        Fixtures.zip("a/b", "child", "a", "file"), Fixtures.zip("a/", "", "a", "file"),
        Fixtures.zip("a", "file", "a/", "") }) {
      Fixtures.Retention store = new Fixtures.Retention();
      assertThrows(IOException.class, () -> unpack(bytes, store));
      assertEquals(0, store.captures);
    }
  }

  @Test
  void rejectsMalformedEmptyDirectoryOnlyAndTruncatedArchives() throws IOException {
    byte[] valid = Fixtures.zip("file", "payload");
    for (byte[] bytes : new byte[][] { new byte[0], "not a ZIP".getBytes(), Fixtures.zip(),
        Fixtures.zip("directory/", ""), Arrays.copyOf(valid, valid.length - 22), Arrays.copyOf(valid, 20) }) {
      assertThrows(IOException.class, () -> unpack(bytes, new Fixtures.Retention()));
    }
  }

  @Test
  void rejectsEncryptionAndUnsupportedCompression() throws IOException {
    byte[] original = Fixtures.zip("file", "data");
    for (boolean encrypted : new boolean[] { true, false }) {
      byte[] altered = original.clone();
      for (int i = 0; i < altered.length - 4; i++) {
        if (signature(altered, i, 0x04034b50))
          altered[i + (encrypted ? 6 : 8)] = (byte) (encrypted ? 1 : 99);
        if (signature(altered, i, 0x02014b50))
          altered[i + (encrypted ? 8 : 10)] = (byte) (encrypted ? 1 : 99);
      }
      assertThrows(IOException.class, () -> unpack(altered, new Fixtures.Retention()));
    }
  }

  @Test
  void validatesStoredAndDeflatedCrcAndDirectoryMetadata() throws IOException {
    byte[] payload = "unique-payload".getBytes();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    CRC32 checksum = new CRC32();
    checksum.update(payload);
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      ZipEntry entry = new ZipEntry("file");
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(payload.length);
      entry.setCrc(checksum.getValue());
      zip.putNextEntry(entry);
      zip.write(payload);
      zip.closeEntry();
    }
    assertEquals(1, unpack(bytes.toByteArray(), new Fixtures.Retention()).size());
    byte[] corruptStored = bytes.toByteArray();
    corruptStored[34] ^= 1;
    assertThrows(IOException.class, () -> unpack(corruptStored, new Fixtures.Retention()));
    for (byte[] corrupt : new byte[][] { Fixtures.zip("file", "data"),
        Fixtures.zip("folder/", "", "folder/file", "data") }) {
      for (int i = 0; i < corrupt.length - 4; i++) {
        if (signature(corrupt, i, 0x02014b50)) {
          corrupt[i + 16] ^= 1;
          break;
        }
      }
      assertThrows(IOException.class, () -> unpack(corrupt, new Fixtures.Retention()));
    }
  }

  @Test
  void enforcesEntryCompressedAndExpandedLimitsAtExactBoundaries() throws IOException {
    byte[] bytes = Fixtures.zip("one", "1234567890", "two", "payload");
    for (Archive limits : new Archive[] { new Archive(1, 1000, 1000), new Archive(10, bytes.length - 1, 1000),
        new Archive(10, 1000, 16) }) {
      assertThrows(IOException.class,
          () -> Archive.unpack(new ByteArrayInputStream(bytes), Path.of(""), new Fixtures.Retention(), limits));
    }
    assertEquals(2, Archive.unpack(new ByteArrayInputStream(bytes), Path.of(""), new Fixtures.Retention(),
        new Archive(2, bytes.length, 17)).size());
    assertThrows(IllegalArgumentException.class, () -> new Archive(0, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new Archive(1, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new Archive(1, 1, -1));
    byte[] forged = bytes.clone();
    for (int i = 0; i < forged.length - 4; i++) {
      if (signature(forged, i, 0x02014b50)) {
        forged[i + 24] = 1;
        forged[i + 25] = forged[i + 26] = forged[i + 27] = 0;
      }
    }
    assertThrows(IOException.class, () -> unpack(forged, new Fixtures.Retention()));
  }

  @SuppressWarnings("resource")
  @Test
  void failureReturnsNoPartialImagesAndLeavesCallerResourcesOpen() throws IOException {
    Fixtures.Source source = new Fixtures.Source(Fixtures.zip("one", "1", "two", "2"));
    Fixtures.Retention store = new Fixtures.Retention();
    store.failAt = 2;
    try (InputStream input = source.open()) {
      assertThrows(IOException.class, () -> Archive.unpack(input, Path.of(""), store, Archive.DEFAULT));
      assertEquals(1, store.contents.size());
      assertEquals(0, source.streamCloses);
      assertEquals(0, store.storeCloses);
    }
    InputStream broken = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("injected source failure");
      }
    };
    assertEquals("injected source failure", assertThrows(IOException.class,
        () -> Archive.unpack(broken, Path.of(""), new Fixtures.Retention(), Archive.DEFAULT)).getMessage());
  }

  @Test
  void acceptsZip64CentralDirectoryWithinConfiguredBounds() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (int i = 0; i < 65_535; i++) {
        ZipEntry directory = new ZipEntry("d" + i + "/");
        directory.setMethod(ZipEntry.STORED);
        directory.setSize(0);
        directory.setCrc(0);
        zip.putNextEntry(directory);
        zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry("file"));
      zip.write(42);
      zip.closeEntry();
    }
    byte[] archive = bytes.toByteArray();
    assertTrue(
        java.util.stream.IntStream.range(0, archive.length - 4).anyMatch(i -> signature(archive, i, 0x06064b50)));
    assertEquals(1, unpack(archive, new Fixtures.Retention()).size());
  }

  private static boolean signature(byte[] bytes, int offset, int expected) {
    for (int i = 0; i < 4; i++)
      if ((bytes[offset + i] & 255) != ((expected >>> (8 * i)) & 255))
        return false;
    return true;
  }
}
