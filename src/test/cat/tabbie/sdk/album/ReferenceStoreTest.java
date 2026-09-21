package cat.tabbie.sdk.album;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import cat.tabbie.sdk.Fixtures;

class ReferenceStoreTest {
  @TempDir
  Path temporary;

  @Test
  void computesKnownDigestsAndIncludesFilenameInEquality() {
    Reference empty = Reference.of("empty.bin", new byte[0]);
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", empty.sha256());
    assertEquals(0, empty.size());
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        Reference.of("abc", "abc".getBytes()).sha256());
    assertNotEquals(empty, Reference.of("other.bin", new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> new Reference("file", empty.sha256(), -1));
    assertThrows(IllegalArgumentException.class, () -> new Reference("file", empty.sha256().toUpperCase(), 0));
    assertThrows(IllegalArgumentException.class, () -> new Reference("file", "0".repeat(63), 0));
    assertThrows(IllegalArgumentException.class, () -> new Reference("file", "g".repeat(64), 0));
    assertThrows(NullPointerException.class, () -> new Reference(null, empty.sha256(), 0));
    assertThrows(NullPointerException.class, () -> new Reference("file", null, 0));
  }

  @ParameterizedTest
  @ValueSource(strings = { "", " ", ".", "..", "a/b", "a\\b", "C:drive", "bad?", "bad*", "bad|", "a\nb", "a\u0000b" })
  void rejectsUnsafeFilenames(String name) {
    assertThrows(IllegalArgumentException.class, () -> Reference.of(name, new byte[0]));
  }

  @SuppressWarnings("resource")
  @Test
  void streamCaptureLeavesCallerOpenAndPathCaptureClosesItsOwnStream() throws IOException {
    Fixtures.Source source = new Fixtures.Source(new byte[] { 1, 2, 3 });
    Fixtures.Retention store = new Fixtures.Retention();
    try (InputStream input = source.open()) {
      assertEquals(3, store.capture(input).size());
      assertEquals(0, source.streamCloses);
    }
    Path file = Files.write(temporary.resolve("named.bin"), source.content);
    Reference reference = store.capture(Files.newInputStream(file));
    assertEquals("named.bin", reference.fileName());
    assertThrows(IOException.class, () -> store.lastInput.read());
    store.failAt = store.captures + 1;
    assertThrows(IOException.class, () -> store.capture(Files.newInputStream(file)));
    assertThrows(IOException.class, () -> store.lastInput.read());
    assertEquals(0, store.storeCloses);
  }

  @SuppressWarnings("resource")
  @Test
  void byteArrayCapturePassesADefensiveCopy() throws IOException {
    byte[] original = { 1, 2, 3 };
    Fixtures.Retention store = new Fixtures.Retention() {
      @Override
      public Reference capture(InputStream input, Observer observer) throws IOException {
        original[0] = 99;
        return super.capture(input, observer);
      }
    };
    assertEquals(Reference.of(store.defaultName, new byte[] { 1, 2, 3 }), store.capture(original));
  }

  @Test
  void sourceOnlyCaptureFailsAndExistenceDoesNotOpenContent() throws IOException {
    Fixtures.Source source = new Fixtures.Source(new byte[] { 1 });
    assertFalse(source.exists());
    source.local = true;
    assertTrue(source.exists());
    assertEquals(0, source.opens);
    assertThrows(IOException.class, () -> source.capture(new byte[0]));
    assertThrows(NullPointerException.class, () -> source.capture((InputStream) null));
    source.close();
    assertEquals(1, source.storeCloses);
  }

  @SuppressWarnings("resource")
  @Test
  void observerGetsProgressVerificationAndFailureInOrder() throws IOException {
    Fixtures.Retention store = new Fixtures.Retention();
    List<String> events = new ArrayList<>();
    Store.Observer observer = new Store.Observer() {
      @Override
      public void transferred(long size, OptionalLong expected) {
        events.add("bytes:" + size);
      }

      @Override
      public void verified(Reference reference) {
        events.add("verified");
      }

      @Override
      public void failed(IOException failure) {
        events.add("failed");
      }
    };
    store.capture(new ByteArrayInputStream(new byte[] { 1 }), observer);
    assertEquals(List.of("bytes:1", "verified"), events);
    store.failAt = store.captures + 1;
    assertThrows(IOException.class, () -> store.capture(new ByteArrayInputStream(new byte[] { 2 }), observer));
    assertEquals(List.of("bytes:1", "verified", "failed"), events);
    assertEquals(1, store.contents.size());
  }

  @Test
  void publicRetentionApiCanReopenByReferenceAndDeduplicateFilenames() throws Exception {
    var capture = assertDoesNotThrow(
        () -> Store.class.getMethod("capture", String.class, InputStream.class, Store.Observer.class),
        "Arbitrary captures and ZIP entries require an explicit filename channel.");
    var open = assertDoesNotThrow(() -> Store.class.getMethod("open", Reference.class),
        "Saved images need a public retained-content reader.");
    var exists = assertDoesNotThrow(() -> Store.class.getMethod("exists", Reference.class));
    Fixtures.Retention retention = new Fixtures.Retention();
    Reference first = (Reference) capture.invoke(retention, "first", new ByteArrayInputStream(new byte[] { 1 }),
        Store.Observer.NONE);
    Reference second = (Reference) capture.invoke(retention, "second", new ByteArrayInputStream(new byte[] { 1 }),
        Store.Observer.NONE);
    assertNotEquals(first, second);
    assertEquals(first.sha256(), second.sha256());
    assertEquals(1, retention.contents.size());
    assertEquals(true, exists.invoke(retention, first));
    try (InputStream a = (InputStream) open.invoke(retention, first);
        InputStream b = (InputStream) open.invoke(retention, second)) {
      assertEquals(1, a.read());
      assertEquals(1, b.read());
    }
    Reference missing = Reference.of("missing", new byte[] { 2 });
    assertEquals(false, exists.invoke(retention, missing));
    assertInstanceOf(IOException.class,
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> open.invoke(retention, missing))
            .getCause());
  }

  @SuppressWarnings("resource")
  @Test
  void namedCaptureDefaultPreservesByteIdentityAndRenamesObserverReference() throws IOException {
    Fixtures.Retention backend = new Fixtures.Retention();
    Store store = new Store() {
      @Override
      public InputStream open() throws IOException {
        return backend.open();
      }

      @Override
      public Reference capture(InputStream input, Observer observer) throws IOException {
        return backend.capture(input, observer);
      }
    };
    List<Reference> verified = new ArrayList<>();
    Store.Observer observer = new Store.Observer() {
      @Override
      public void verified(Reference reference) {
        verified.add(reference);
      }
    };
    Reference captured = store.capture(new ByteArrayInputStream(new byte[] { 1 }), observer);
    assertEquals(Reference.of("explicit.bin", new byte[] { 1 }), captured);
    assertEquals(List.of(captured), verified);
    assertFalse(store.exists());
  }
}
