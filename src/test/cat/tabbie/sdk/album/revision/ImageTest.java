package cat.tabbie.sdk.album.revision;

import static cat.tabbie.sdk.TestFixtures.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.platform.Package.Manager;
import cat.tabbie.sdk.platform.Relative;

class ImageTest {

  private static final Relative.Root JAR = Relative.root("mods", "example.jar");

  private static final Reference.Captured FIRST = Reference.of("example.jar", bytes("first"));

  private static final Reference.Captured SECOND = Reference.of("example.jar", bytes("second"));

  private static final Reference.Captured THIRD = Reference.of("example.jar", bytes("third"));

  private static final Package JAVA = new Package("java-runtime",
      Map.of(Manager.APT, new Package.Specification("openjdk-21-jre-headless")));

  private static final Package MISSING = new Package("tabbie-missing",
      Map.of(Manager.APT, new Package.Specification("tabbie-missing-package")));

  private Path mount;

  /**
   * Creates a fresh directory for scrutinized files. File targets resolve
   * against the working directory, so it lies beneath it, in the build
   * directory.
   */
  private Relative.Root mount() throws IOException {
    Path parent = Files.createDirectories(Path.of("build", "tmp", "scrutiny"));
    mount = Files.createTempDirectory(parent, "image-");
    return Relative.root("build", "tmp", "scrutiny", mount.getFileName().toString());
  }

  @AfterEach
  void deleteMount() throws IOException {
    if (mount == null) {
      return;
    }
    try (Stream<Path> paths = Files.walk(mount)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        path.toFile().setWritable(true);
        Files.delete(path);
      }
    }
  }

  @Test
  void installation_scrutinizesToAPassOfItsAfterState_whenTheFileIsInItsBeforeState() throws IOException {
    Relative.Root jar = mount().resolve("mods", "example.jar");

    Scrutiny<Image.File> created = Image.create(jar, FIRST).scrutinize();
    Files.createDirectories(mount.resolve("mods"));
    Files.write(mount.resolve("mods").resolve("example.jar"), bytes("first"));
    Scrutiny<Image.File> replaced = Image.replace(jar, FIRST, SECOND).scrutinize();
    Scrutiny<Image.File> deleted = Image.delete(jar, Reference.of("renamed.jar", bytes("first"))).scrutinize();

    assertEquals(new Scrutiny.Pass<>(new Image.File.Present(FIRST)), created);
    assertEquals(new Scrutiny.Pass<>(new Image.File.Present(SECOND)), replaced);
    assertEquals(new Scrutiny.Pass<>(new Image.File.Absent()), deleted);
  }

  @Test
  void installation_failsWithTheFileFound_whenItChangedSinceRehearsal() throws IOException {
    Relative.Root root = mount();
    Files.write(mount.resolve("example.jar"), bytes("second"));

    Scrutiny<Image.File> changed = Image.replace(root.resolve("example.jar"), FIRST, THIRD).scrutinize();
    Scrutiny<Image.File> created = Image.create(root.resolve("example.jar"), FIRST).scrutinize();
    Scrutiny<Image.File> deleted = Image.delete(root.resolve("other.jar"), FIRST).scrutinize();

    assertEquals(new Scrutiny.Fail<>(new Image.File.Present(SECOND)), changed);
    assertEquals(new Scrutiny.Fail<>(new Image.File.Present(SECOND)), created);
    assertEquals(new Scrutiny.Fail<>(new Image.File.Absent()), deleted);
  }

  @Test
  void installation_failsWithoutAState_whenItsPathIsADirectory_orBeneathAFile() throws IOException {
    Relative.Root root = mount();
    Files.createDirectory(mount.resolve("example.jar"));
    Files.write(mount.resolve("mods"), bytes("first"));

    Scrutiny<Image.File> directory = Image.create(root.resolve("example.jar"), FIRST).scrutinize();
    Scrutiny<Image.File> beneathAFile = Image.create(root.resolve("mods", "example.jar"), FIRST).scrutinize();

    assertInstanceOf(Scrutiny.Fail.class, directory);
    assertNull(directory.result());
    assertInstanceOf(Scrutiny.Fail.class, beneathAFile);
    assertNull(beneathAFile.result());
  }

  @Test
  void installation_failsWithoutAState_whenTheFileIsNotWritable() throws IOException {
    Relative.Root root = mount();
    Path file = Files.write(mount.resolve("example.jar"), bytes("first"));
    assumeTrue(file.toFile().setWritable(false) && !Files.isWritable(file));

    Scrutiny<Image.File> scrutiny = Image.replace(root.resolve("example.jar"), FIRST, SECOND).scrutinize();

    assertInstanceOf(Scrutiny.Fail.class, scrutiny);
    assertNull(scrutiny.result());
  }

  @Test
  void configuration_passesWhenItsChunksMatch_whateverTheLinesOutsideThem() throws IOException {
    Relative.Root properties = mount().resolve("example.properties");
    Image<Image.Text> image = Image.configure(properties, "a=1\nb=2\n", "a=1\nb=3\n", 0);
    Files.writeString(mount.resolve("example.properties"), "a=9\nb=2\n");

    Scrutiny<Image.Text> scrutiny = image.scrutinize();

    assertEquals(new Scrutiny.Pass<>(image.after()), scrutiny);
  }

  @Test
  void configuration_failsWithTheCurrentText_whenItsLinesChanged_andWithoutOne_whenTheFileIsGone()
      throws IOException {
    Relative.Root properties = mount().resolve("example.properties");
    Path file = mount.resolve("example.properties");
    Image<Image.Text> image = Image.configure(properties, "a=1\nb=2\n", "a=1\nb=3\n");

    Files.writeString(file, "a=1\nb=9\n");
    Scrutiny<Image.Text> changed = image.scrutinize();
    Files.writeString(file, "a=1\r\nb=2\r\n");
    Scrutiny<Image.Text> otherEndings = image.scrutinize();
    Files.delete(file);
    Scrutiny<Image.Text> gone = image.scrutinize();

    assertEquals(new Scrutiny.Fail<>(new Image.Text(Chunk.fragmentsOf("a=1\nb=9\n"))), changed);
    assertEquals(new Scrutiny.Fail<>(new Image.Text(Chunk.fragmentsOf("a=1\r\nb=2\r\n"))), otherEndings);
    assertInstanceOf(Scrutiny.Fail.class, gone);
    assertNull(gone.result());
  }

  @Test
  void scrutinize_rejectsWorldScopedFiles_whoseWorldMountIsUnknown() {
    Relative.World level = Relative.world("level.dat");

    assertThrows(IOException.class, () -> Image.create(level, FIRST).scrutinize());
    assertThrows(IOException.class, () -> Image.configure(level, "a\n", "b\n").scrutinize());
  }

  @Test
  void provision_failsWithTheClaimFound_whenNoManagerHoldsTheReleasedPackage() throws IOException {
    Image<Image.Claim> released = Image.deprovision(MISSING);

    Scrutiny<Image.Claim> scrutiny = released.scrutinize();

    assertEquals(new Scrutiny.Fail<>(new Image.Claim.Absent()), scrutiny);
  }

  @Test
  void fileFactories_describeExistenceChanges_andPreimagesSwapThem() {
    Image<Image.File> created = Image.create(JAR, FIRST);
    Image<Image.File> replaced = Image.replace(JAR, FIRST, SECOND);
    Image<Image.File> deleted = Image.delete(JAR, FIRST);

    assertInstanceOf(Image.File.Absent.class, created.before());
    assertEquals(new Image.File.Present(FIRST), created.after());
    assertEquals(new Image.File.Present(SECOND), replaced.after());
    assertInstanceOf(Image.File.Absent.class, deleted.after());
    assertEquals(deleted, created.preimage());
    assertEquals(Image.replace(JAR, SECOND, FIRST), replaced.preimage());
    assertEquals(new Image.Target.File(JAR), created.target());
  }

  @Test
  void installation_rejectsAbsenceOnBothSides_andTargetsBeneathTheMount() {
    assertThrows(IllegalArgumentException.class, () -> new Image.Installation(
        new Image.File.Absent(), new Image.File.Absent(), new Image.Target.File(JAR)));
    assertThrows(IllegalArgumentException.class, () -> new Image.Target.File(Relative.root()));
    assertThrows(IllegalArgumentException.class, () -> new Image.Target.File(Relative.world()));
  }

  @Test
  void configure_describesSelectedRegions_andEqualTextAssertsOnlyExistence() {
    Relative.Root path = Relative.root("config", "example.properties");

    Image<Image.Text> changed = Image.configure(path, "a=1\nb=2\n", "a=1\nb=3\n");
    Image<Image.Text> unchanged = Image.configure(path, "a=1\n", "a=1\n");

    assertEquals("a=1\nb=2\n", changed.before().fragments().getFirst().text());
    assertEquals("a=1\nb=3\n", changed.after().fragments().getFirst().text());
    assertEquals(changed.before(), changed.preimage().after());
    assertEquals(List.of(), unchanged.before().fragments());
    assertEquals(List.of(), unchanged.after().fragments());
  }

  @Test
  void configure_pairsExplicitFragmentStates() {
    Relative.Root path = Relative.root("config.txt");
    Image.Text before = new Image.Text(List.of(new Chunk.Fragment(Chunk.linesOf("x\n"), new Chunk.Range(2, 1))));
    Image.Text after = new Image.Text(List.of(new Chunk.Fragment(Chunk.linesOf("y\n"), new Chunk.Range(2, 1))));

    Image<Image.Text> image = Image.configure(path, before, after);

    assertEquals(before, image.before());
    assertEquals(after, image.after());
  }

  @Test
  void provision_carriesOptions_andRejectsAbsenceOnBothSidesOrDisjointManagers() {
    Map<Manager, List<String>> install = new HashMap<>(Map.of(Manager.APT, List.of("--no-install-recommends")));
    Map<Manager, List<String>> undo = Map.of(Manager.APT, List.of("--purge"));

    Image<Image.Claim> image = Image.provision(JAVA, install, undo);
    install.put(Manager.DNF, List.of());

    assertEquals(new Image.Claim.Present(Map.of(Manager.APT, List.of("--no-install-recommends"))), image.after());
    assertEquals(new Image.Claim.Absent(undo), image.before());
    assertEquals(Image.deprovision(JAVA, undo, Map.of(Manager.APT, List.of("--no-install-recommends"))),
        image.preimage());
    assertThrows(IllegalArgumentException.class, () -> new Image.Provision(
        new Image.Claim.Absent(), new Image.Claim.Absent(), new Image.Target.Pack(JAVA)));
    assertThrows(IllegalArgumentException.class, () -> Image.provision(JAVA,
        Map.of(Manager.APT, List.of()), Map.of(Manager.DNF, List.of())));
  }

  @Test
  void claimOptions_areImmutableCopies() {
    List<String> options = new ArrayList<>(List.of("--yes"));

    Image.Claim.Present claim = new Image.Claim.Present(Map.of(Manager.APT, options));
    options.add("--later");

    assertEquals(List.of("--yes"), claim.installOption().get(Manager.APT));
    assertThrows(UnsupportedOperationException.class, () -> claim.installOption().put(Manager.DNF, List.of()));
  }

  @Test
  void validate_requiresUniqueTargets_andNoFileUsedAsADirectory() {
    Image<?> jar = Image.create(JAR, FIRST);
    Image<?> config = Image.configure(JAR, "a\n", "b\n");
    Image<?> nested = Image.create(JAR.resolve("inner.txt"), SECOND);
    Image<?> sameNameInWorld = Image.create(Relative.world("mods", "example.jar"), SECOND);
    Image<?> claim = Image.provision(JAVA);

    Image.validate(List.of(jar, sameNameInWorld, claim));

    assertThrows(IllegalArgumentException.class, () -> Image.validate(List.of(jar, config)));
    assertThrows(IllegalArgumentException.class, () -> Image.validate(List.of(jar, nested)));
    assertThrows(IllegalArgumentException.class, () -> Image.validate(List.of(claim, Image.deprovision(JAVA))));
  }

  @Test
  void preimageOfASet_undoesEveryImage() {
    Set<Image<?>> images = Set.of(Image.create(JAR, FIRST), Image.provision(JAVA));

    Set<Image<?>> undone = Image.preimage(images);

    assertEquals(Set.of(Image.delete(JAR, FIRST), Image.deprovision(JAVA)), undone);
  }

  @Test
  void compose_foldsWholeFileChanges_fromFirstBeforeToSecondAfter() {
    Optional<Image<?>> createThenReplace = Image.compose(Image.create(JAR, FIRST), Image.replace(JAR, FIRST, SECOND));
    Optional<Image<?>> replaceTwice = Image.compose(Image.replace(JAR, FIRST, SECOND),
        Image.replace(JAR, SECOND, THIRD));
    Optional<Image<?>> createThenDelete = Image.compose(Image.create(JAR, FIRST), Image.delete(JAR, FIRST));

    assertEquals(Optional.of(Image.create(JAR, SECOND)), createThenReplace);
    assertEquals(Optional.of(Image.replace(JAR, FIRST, THIRD)), replaceTwice);
    assertEquals(Optional.empty(), createThenDelete);
  }

  @Test
  void compose_matchesWholeFileStatesByContent_ignoringRetainedNames() {
    Reference.Captured renamed = Reference.of("renamed.jar", bytes("first"));

    Optional<Image<?>> replaced = Image.compose(Image.create(JAR, FIRST), Image.replace(JAR, renamed, SECOND));
    Optional<Image<?>> cancelled = Image.compose(Image.create(JAR, FIRST), Image.delete(JAR, renamed));

    assertEquals(Optional.of(Image.create(JAR, SECOND)), replaced);
    assertEquals(Optional.empty(), cancelled);
  }

  @Test
  void compose_rejectsOtherTargets_discontinuity_andMixedKinds() {
    Image<?> created = Image.create(JAR, FIRST);

    assertThrows(IllegalArgumentException.class,
        () -> Image.compose(created, Image.delete(Relative.root("other.jar"), FIRST)));
    assertThrows(IllegalArgumentException.class, () -> Image.compose(created, Image.delete(JAR, SECOND)));
    assertThrows(Image.IrreducibleException.class, () -> Image.compose(created, Image.configure(JAR, "a\n", "b\n")));
  }

  @Test
  void compose_cancelsAClaimTakenAndReleased_evenWithDifferentOptions() {
    Image<Image.Claim> claimed = Image.provision(JAVA);
    Image<Image.Claim> released = Image.deprovision(JAVA, Map.of(Manager.APT, List.of("--purge")), Map.of());

    Optional<Image<?>> net = Image.compose(claimed, released);

    assertEquals(Optional.empty(), net);
  }

  @Test
  void compose_foldsTextChanges_andCancelsAnInverse() {
    Relative.Root path = Relative.root("config.txt");
    Image<Image.Text> first = Image.configure(path, "a\nb\nc\n", "a\nB\nc\n");
    Image<Image.Text> second = Image.configure(path, "a\nB\nc\n", "a\nB\nC\n");

    Image<?> composed = Image.compose(first, second).orElseThrow();
    Optional<Image<?>> cancelled = Image.compose(first, first.preimage());

    assertEquals("a\nB\nC\n", assertInstanceOf(Image.Configuration.class, composed).after().fragments().getFirst()
        .text());
    assertEquals(Optional.empty(), cancelled);
  }

  @Test
  void compose_decidesTextChangesByTheirChunks_ratherThanByEqualFragments() {
    Relative.Root path = Relative.root("config.txt");
    Image.Configuration first = (Image.Configuration) Image.configure(path, "a\nc\n", "b\nc\n", 0);
    Image.Configuration second = (Image.Configuration) Image.configure(path, "b\nc\n", "a\n", 0);
    Image<Image.Text> earlier = Image.configure(path, "a\n", "b\n", 0);
    Image<Image.Text> unrelated = Image.configure(path, "ZZZ\n", "a\n", 0);

    Optional<Image<?>> composed = Image.compose(first, second);

    assertEquals(Optional.of(new Image.Configuration(Chunk.compose(first.chunks(), second.chunks()), first.target())),
        composed);
    assertThrows(IllegalArgumentException.class, () -> Image.compose(earlier, unrelated));
  }

  @Test
  void compose_cancelsAClaim_releasedWithOtherOptionsThanItWasTakenWith() {
    Image<Image.Claim> claimed = Image.provision(JAVA, Map.of(Manager.APT, List.of("--no-install-recommends")),
        Map.of(Manager.APT, List.of("--purge")));

    assertEquals(Optional.empty(), Image.compose(claimed, Image.deprovision(JAVA)));
  }

  @Test
  void reduce_requiresChangesAfterACancelledPair_toContinueFromTheRestoredState() {
    Image<?> replaced = Image.replace(JAR, FIRST, SECOND);
    Image<?> restored = Image.replace(JAR, SECOND, FIRST);
    List<Set<Image<?>>> stale = List.of(Set.of(replaced), Set.of(restored), Set.of(Image.replace(JAR, SECOND, THIRD)));
    List<Set<Image<?>>> deletedTwice = List.of(Set.of(Image.create(JAR, FIRST)), Set.of(Image.delete(JAR, FIRST)),
        Set.of(Image.delete(JAR, FIRST)));
    List<Set<Image<?>>> releasedTwice = List.of(Set.of(Image.provision(JAVA)), Set.of(Image.deprovision(JAVA)),
        Set.of(Image.deprovision(JAVA)));
    List<Set<Image<?>>> continued = List.of(Set.of(replaced), Set.of(restored),
        Set.of(Image.replace(JAR, FIRST, THIRD)));

    assertThrows(IllegalArgumentException.class, () -> Image.reduce(stale));
    assertThrows(IllegalArgumentException.class, () -> Image.reduce(deletedTwice));
    assertThrows(IllegalArgumentException.class, () -> Image.reduce(releasedTwice));
    assertEquals(Set.of(Image.replace(JAR, FIRST, THIRD)), Image.reduce(continued));
  }

  @Test
  void reduce_foldsSequentialSets_andDropsTargetsThatCancel() {
    List<Set<Image<?>>> changes = List.of(
        Set.of(Image.create(JAR, FIRST), Image.provision(JAVA)),
        Set.of(Image.replace(JAR, FIRST, SECOND)),
        Set.of(Image.deprovision(JAVA)));

    Set<Image<?>> net = Image.reduce(changes);

    assertEquals(Set.of(Image.create(JAR, SECOND)), net);
  }

  @Test
  void reduce_rejectsInvalidSets_andOrderDependentNetChanges() {
    Relative.Root directory = Relative.root("mods", "example");
    List<Set<Image<?>>> invalid = List.of(Set.of(Image.create(JAR, FIRST), Image.configure(JAR, "a\n", "b\n")));
    List<Set<Image<?>>> fileBecomesDirectory = List.of(
        Set.of(Image.delete(directory, FIRST)),
        Set.of(Image.create(directory.resolve("inner.jar"), SECOND)));

    assertThrows(IllegalArgumentException.class, () -> Image.reduce(invalid));
    assertThrows(IllegalArgumentException.class, () -> Image.reduce(fileBecomesDirectory));
  }

  @Test
  void text_rejectsOverlappingFragments() {
    List<Chunk.Fragment> overlapping = List.of(
        new Chunk.Fragment(Chunk.linesOf("a\nb\n"), new Chunk.Range(0, 2)),
        new Chunk.Fragment(Chunk.linesOf("c\n"), new Chunk.Range(1, 1)));

    assertThrows(IllegalArgumentException.class, () -> new Image.Text(overlapping));
    assertTrue(new Image.Text(List.of()).fragments().isEmpty());
  }
}
