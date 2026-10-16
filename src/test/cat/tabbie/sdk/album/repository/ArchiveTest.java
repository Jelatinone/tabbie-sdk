package cat.tabbie.sdk.album.repository;

import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.collapse;
import static cat.tabbie.sdk.TestFixtures.entries;
import static cat.tabbie.sdk.TestFixtures.scrutinize;
import static cat.tabbie.sdk.TestFixtures.source;
import static cat.tabbie.sdk.TestFixtures.zip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import cat.tabbie.sdk.TestFixtures.MockRetention;
import cat.tabbie.sdk.TestFixtures.MockSource;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.album.revision.Scrutiny;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Relative;

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
  void unpack_readsTheArchiveOnceToInspect_andOnceToCapture_whateverItsEntryCount() throws IOException {
    MockRetention retention = new MockRetention();
    String[] names = Stream.iterate(0, index -> index + 1).limit(50)
        .flatMap(index -> Stream.of("dir/file-" + index + ".txt", "content " + index))
        .toArray(String[]::new);
    MockSource archive = source("pack.zip", zip(entries(names)));

    Set<Image<?>> images = collapse(Archive.DEFAULT.unpack(archive, retention, Relative.root("pack")), scratch);

    assertEquals(50, images.size());
    assertEquals(2, archive.opens());
  }

  @Test
  void unpack_givesTheBackendEntriesItMayOpenMoreThanOnce() throws IOException {
    MockRetention retention = new MockRetention();
    Extract hashFirst = new Extract() {

      @Override
      public Intermediate<Reference.Captured> capture(Describe source,
          Observer<? super Reference.Captured, ? super Transfer> observer) {
        return Intermediate.of(context -> {
          try (InputStream first = source.open()) {
            first.transferTo(OutputStream.nullOutputStream());
          }
          return retention.capture(source, observer).collapse(context);
        });
      }

      @Override
      public Describe retained(Reference.Captured reference) {
        return retention.retained(reference);
      }
    };
    MockSource archive = source("pack.zip", zip(entries("data/a.txt", "alpha")));

    Image<?> image = collapse(Archive.DEFAULT.unpack(archive, hashFirst, Relative.root("pack")), scratch).iterator()
        .next();

    Image.Installation created = assertInstanceOf(Image.Installation.class, image);
    assertArrayEquals(bytes("alpha"),
        retention.read(assertInstanceOf(Image.File.Present.class, created.after()).reference()));
  }

  @Test
  void unpack_extractsStoredEntries_whoseSizesFollowTheirData() throws IOException {
    // Written by a streaming ZIP writer: a stored entry with a data descriptor
    byte[] streamed = Base64.getDecoder().decode("UEsDBBQACAAAALcBTF0AAAAAAAAAAAAAAAAKAAAAc3RvcmVkLnR4dHN0b3"
        + "JlZCB2aWEgdW5zZWVrYWJsZSBzdHJlYW1QSwcITvpkhRwAAAAcAAAAUEsBAhQAFAAIAAAAtwFMXU76ZIUcAAAAHAAAAAoAAAAAAAAAAAAAA"
        + "IABAAAAAHN0b3JlZC50eHRQSwUGAAAAAAEAAQA4AAAAVAAAAAAA");
    MockRetention retention = new MockRetention();

    Image<?> image = collapse(Archive.DEFAULT.unpack(source("streamed.zip", streamed), retention,
        Relative.root("pack")), scratch).iterator().next();

    Image.Installation created = assertInstanceOf(Image.Installation.class, image);
    assertEquals(Relative.root("pack", "stored.txt"), created.target().relative());
    assertArrayEquals(bytes("stored via unseekable stream"),
        retention.read(assertInstanceOf(Image.File.Present.class, created.after()).reference()));
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
  void inspect_scrutinizesToAPass_withTheEntriesOfACollapse() throws IOException {
    MockSource archive = source("pack.zip", zip(entries("assets/a.txt", "alpha", "b.txt", "")));

    Scrutiny<List<Archive.Entry>> scrutiny = scrutinize(Archive.DEFAULT.inspect(archive), scratch);
    List<Archive.Entry> found = collapse(Archive.DEFAULT.inspect(archive), scratch);

    assertEquals(new Scrutiny.Pass<>(found), scrutiny);
  }

  @Test
  void unpack_scrutinizesToAPass_withTheImagesOfACollapse_andRetainsItsCaptures() throws IOException {
    MockRetention scrutinized = new MockRetention();
    MockRetention collapsed = new MockRetention();
    MockSource archive = source("pack.zip", zip(entries("data/a.txt", "alpha", "b.txt", "")));

    Scrutiny<Set<Image<?>>> scrutiny = scrutinize(
        Archive.DEFAULT.unpack(archive, scrutinized, Relative.root("pack")), scratch);
    Set<Image<?>> images = collapse(Archive.DEFAULT.unpack(archive, collapsed, Relative.root("pack")), scratch);

    assertEquals(new Scrutiny.Pass<>(images), scrutiny);
    assertTrue(images.stream()
        .map(image -> assertInstanceOf(Image.Installation.class, image).after())
        .map(after -> assertInstanceOf(Image.File.Present.class, after).reference())
        .allMatch(scrutinized::exists));
  }

  @Test
  void unpack_mocks_whenItsBackendCapturesWithAPlainEffect() throws IOException {
    MockRetention retention = new MockRetention();
    Extract effectful = new Extract() {

      @Override
      public Intermediate<Reference.Captured> capture(Describe source,
          Observer<? super Reference.Captured, ? super Transfer> observer) {
        return Intermediate.of(context -> retention.capture(source, observer).collapse(context));
      }

      @Override
      public Describe retained(Reference.Captured reference) {
        return retention.retained(reference);
      }
    };
    MockSource archive = source("pack.zip", zip(entries("data/a.txt", "alpha")));

    Scrutiny<Set<Image<?>>> scrutiny = scrutinize(
        Archive.DEFAULT.unpack(archive, effectful, Relative.root("pack")), scratch);

    assertEquals(new Scrutiny.Mock<>(), scrutiny);
    assertEquals(0, retention.captures());
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
  void inspectAndUnpack_nameTheirArchive_andWhereItUnpacks() throws IOException {
    byte[] content = zip(entries("data/a.txt", "alpha", "b.txt", ""));
    MockSource declared = new MockSource(new Reference.Pending("pack.zip", null, (long) content.length), content);
    MockSource undeclared = source("pack.zip", content);
    List<Archive.Entry> found = collapse(Archive.DEFAULT.inspect(undeclared), scratch);

    String sized = assertInstanceOf(Intermediate.Step.class, Archive.DEFAULT.inspect(declared)).name();
    String unsized = assertInstanceOf(Intermediate.Step.class, Archive.DEFAULT.inspect(undeclared)).name();
    String unpack = new Archive.Unpack(new MockRetention(), Relative.world("datapacks", "x"), found, Archive.DEFAULT)
        .name();

    assertEquals("Inspect pack.zip (" + Archive.Operand.BYTE.render(content.length) + ")", sized);
    assertEquals("Inspect pack.zip", unsized);
    assertEquals("Unpack 2 files from pack.zip into world:/datapacks/x", unpack);
  }

  @Test
  void unpack_reportsItsProgressAndOutcome_toTheContextObserver() throws IOException {
    byte[] content = zip(entries("data/a.txt", "alpha", "b.txt", "", "c.txt", "gamma"));
    MockSource archive = new MockSource(new Reference.Pending("pack.zip", null, (long) content.length), content);
    Recorder recorder = new Recorder();

    Archive.DEFAULT.unpack(archive, new MockRetention(), Relative.root("pack"))
        .collapse(new Intermediate.Step.Context.Default(scratch, recorder));

    String copied = "pack.zip  " + Archive.Operand.BYTE.render(content.length);
    assertEquals(List.of(copied, copied), rendered(recorder.transfers, "pack.zip", Archive.Operand.BYTE));
    assertEquals(List.of(1, 2, 3), completed(recorder.transfers, Archive.Operand.ENTRY));
    assertEquals(List.of("data/a.txt  5 B"), rendered(recorder.transfers, "data/a.txt", Archive.Operand.BYTE));
    assertEquals(List.of(1, 2, 3), completed(recorder.transfers, Archive.Operand.FILE));
    assertEquals(List.of("pack.zip  3 files", "pack.zip  3 files"),
        recorder.verified.stream().map(Archive.Observe::render).toList());
    assertTrue(recorder.failures.isEmpty());
  }

  @Test
  void inspect_reportsTheFailureThatEndedIt_withoutAnOutcome() {
    Recorder recorder = new Recorder();
    MockSource corrupt = source("pack.zip", bytes("this is not a zip archive"));

    IOException thrown = assertThrows(IOException.class,
        () -> Archive.DEFAULT.inspect(corrupt).collapse(new Intermediate.Step.Context.Default(scratch, recorder)));

    assertEquals(List.of(thrown), recorder.failures);
    assertTrue(recorder.verified.isEmpty());
  }

  @Test
  void observe_rendersProgressAgainstItsTotal_andAnOutcomeAlone() {
    Archive.Observe<Long> bytes = new Archive.Observe<>("pack.zip", 1L << 20, 4L << 20, Archive.Operand.BYTE,
        Duration.ZERO);
    Archive.Observe<Integer> entries = new Archive.Observe<>("pack.zip", 3, 40, Archive.Operand.ENTRY, Duration.ZERO);
    Archive.Observe<Integer> outcome = new Archive.Observe<>("pack.zip", 1, 1, Archive.Operand.FILE, Duration.ZERO);

    assertEquals("pack.zip  1.0 MiB of 4.0 MiB (25%)", bytes.render());
    assertEquals("pack.zip  3 entries of 40 entries (7%)", entries.render());
    assertEquals("pack.zip  1 file", outcome.render());
    assertThrows(IllegalArgumentException.class,
        () -> new Archive.Observe<>("pack.zip", -1, 1, Archive.Operand.FILE, Duration.ZERO));
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

  private static List<String> rendered(List<Archive.Observe<?>> reports, String subject, Archive.Operand unit) {
    return reports.stream()
        .filter(report -> report.subject().equals(subject) && report.unit() == unit)
        .map(Archive.Observe::render)
        .toList();
  }

  private static List<Number> completed(List<Archive.Observe<?>> reports, Archive.Operand unit) {
    return reports.stream()
        .filter(report -> report.unit() == unit)
        .<Number>map(Archive.Observe::completed)
        .toList();
  }

  /**
   * A context observer recording the archive reports it receives.
   */
  private static final class Recorder implements Observer<Observer.Renderable, Observer.Renderable> {

    final List<Archive.Observe<?>> transfers = new ArrayList<>();

    final List<Archive.Observe<?>> verified = new ArrayList<>();

    final List<Exception> failures = new ArrayList<>();

    @Override
    public void transfer(Observer.Renderable transfer) {
      if (transfer instanceof Archive.Observe<?> observe) {
        transfers.add(observe);
      }
    }

    @Override
    public void verified(Observer.Renderable observe) {
      if (observe instanceof Archive.Observe<?> outcome) {
        verified.add(outcome);
      }
    }

    @Override
    public void failed(Exception failure) {
      failures.add(failure);
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
