package cat.tabbie.sdk.addon;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import cat.tabbie.sdk.Fixtures;
import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Datapack;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.addon.artifact.Modpack;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Reference;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;

class ArtifactTest {
  static final Identity<Artifact> ID = Identity.create("test:addon:build:artifact");
  static final Reference CONTENT = Reference.of("example.bin", new byte[] { 1, 2, 3 });
  static final Label FABRIC = Fixtures.target(Mod.class);

  static Stream<Arguments> variants() {
    return Fixtures.families().flatMap(family -> Stream.of(Arguments.of(family, false), Arguments.of(family, true)));
  }

  static Artifact.Context context(Label label, Store store, Path root,
      Map<Identity<Artifact>, Artifact.Layout> overrides) {
    return new Artifact.Context() {
      @Override
      public Label label() {
        return label;
      }

      @Override
      public Store store() {
        return store;
      }

      @Override
      public Path contextRoot() {
        return root;
      }

      @Override
      public Path worldRoot() {
        return Path.of("worlds/main");
      }

      @Override
      public Map<Identity<Artifact>, Artifact.Layout> layouts() {
        return overrides;
      }
    };
  }

  @ParameterizedTest(name = "{0} custom={1}")
  @MethodSource("variants")
  void constructsWithoutIoCopiesCollectionsAndInvokesChosenArtist(Class<? extends Artifact> family, boolean custom)
      throws Exception {
    Fixtures.Source source = new Fixtures.Source(new byte[] { 1, 2, 3 });
    Fixtures.Retention retention = new Fixtures.Retention();
    Set<Label> labels = new HashSet<>(Set.of(Fixtures.target(family)));
    Set<Identity<Artifact>> depends = new HashSet<>(Set.of(Identity.create("dependency")));
    Set<Identity<Artifact>> conflicts = new HashSet<>(Set.of(Identity.create("conflict")));
    AtomicInteger callbacks = new AtomicInteger();
    Artifact.Context context = context(Fixtures.target(family), retention, Path.of("den"),
        Map.of(ID, new Artifact.Layout.Root(false, Path.of("selected"))));
    Artifact artifact = Fixtures.artifact(family, custom, ID, "Example", source, labels, depends, conflicts,
        (canvas, supplied) -> {
          assertTrue(family.isInstance(canvas));
          assertSame(context, supplied);
          callbacks.incrementAndGet();
          return Set.of(Image.create(Path.of("den/custom.bin"), CONTENT));
        });
    labels.clear();
    depends.clear();
    conflicts.clear();
    assertAll(
        () -> assertEquals(Set.of(Fixtures.target(family)), artifact.labels()),
        () -> assertEquals(Set.of(Identity.create("dependency")), artifact.depends()),
        () -> assertEquals(Set.of(Identity.create("conflict")), artifact.conflicts()),
        () -> assertThrows(UnsupportedOperationException.class, () -> artifact.labels().clear()),
        () -> assertThrows(UnsupportedOperationException.class, () -> artifact.depends().clear()),
        () -> assertThrows(UnsupportedOperationException.class, () -> artifact.conflicts().clear()),
        () -> assertEquals(0, source.opens),
        () -> assertEquals(0, retention.captures));
    Set<Image<?>> images = artifact.images(context);
    assertEquals(1, images.size());
    Image<?> image = images.iterator().next();
    assertEquals(Path.of(custom ? "den/custom.bin" : "den/selected/example.bin"), image.path());
    assertEquals(CONTENT, ((Image.File.Present) image.after()).reference());
    assertThrows(UnsupportedOperationException.class, images::clear);
    assertEquals(custom ? 1 : 0, callbacks.get());
    assertEquals(custom ? 0 : 1, source.opens);
    assertEquals(source.opens, source.streamCloses);
    assertEquals(0, retention.opens);
    assertEquals(0, retention.storeCloses);
    assertEquals(0, source.storeCloses);
  }

  @ParameterizedTest(name = "{0} custom={1}")
  @MethodSource("variants")
  void rejectsInvalidNamesRelationshipsAndNullComponents(Class<? extends Artifact> family, boolean custom) {
    Fixtures.Source source = new Fixtures.Source(new byte[0]);
    Set<Label> labels = Set.of(Fixtures.target(family));
    Artifact.Artist<Artifact> artist = (a, c) -> Set.of();
    var external = Identity.<Artifact>create("external");
    assertAll(
        () -> assertThrows(IllegalArgumentException.class,
            () -> Fixtures.artifact(family, custom, ID, " ", source, labels, Set.of(), Set.of(), artist)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", source, Set.of(), Set.of(), Set.of(), artist)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", source, labels, Set.of(ID), Set.of(), artist)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", source, labels, Set.of(), Set.of(ID), artist)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", source, labels, Set.of(external), Set.of(external),
                artist)),
        () -> assertThrows(NullPointerException.class,
            () -> Fixtures.artifact(family, custom, null, "Name", source, labels, Set.of(), Set.of(), artist)),
        () -> assertThrows(NullPointerException.class,
            () -> Fixtures.artifact(family, custom, ID, null, source, labels, Set.of(), Set.of(), artist)),
        () -> assertThrows(NullPointerException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", null, labels, Set.of(), Set.of(), artist)),
        () -> assertThrows(NullPointerException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", source, null, Set.of(), Set.of(), artist)),
        () -> assertThrows(NullPointerException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", source, labels, null, Set.of(), artist)),
        () -> assertThrows(NullPointerException.class,
            () -> Fixtures.artifact(family, custom, ID, "Name", source, labels, Set.of(), null, artist)),
        () -> assertThrows(NullPointerException.class, () -> Fixtures.artifact(family, custom, ID, "Name", source,
            new HashSet<>(Arrays.asList((Label) null)), Set.of(), Set.of(), artist)));
    if (custom)
      assertThrows(NullPointerException.class,
          () -> Fixtures.artifact(family, true, ID, "Name", source, labels, Set.of(), Set.of(), null));
    assertEquals(0, source.opens);
  }

  @ParameterizedTest
  @MethodSource("variants")
  void rejectsUnknownTargetsBeforeCallingAnyArtist(Class<? extends Artifact> family, boolean custom) throws Exception {
    Fixtures.Source source = new Fixtures.Source(new byte[0]);
    AtomicInteger calls = new AtomicInteger();
    Label declared = Fixtures.target(family);
    Label other = new Label(declared.version(), declared.distribution(), declared.environment());
    cat.tabbie.sdk.minecraft.Version changed = declared.version() instanceof cat.tabbie.sdk.minecraft.Version.Java
        ? new cat.tabbie.sdk.minecraft.Version.Java("different", cat.tabbie.sdk.minecraft.Version.Java.Release.UNKNOWN,
            java.time.Instant.EPOCH)
        : new cat.tabbie.sdk.minecraft.Version.Bedrock("different",
            cat.tabbie.sdk.minecraft.Version.Bedrock.Release.UNKNOWN, java.time.Instant.EPOCH);
    other = new Label(changed, other.distribution(), other.environment());
    Artifact artifact = Fixtures.artifact(family, custom, ID, "Name", source, Set.of(declared), Set.of(), Set.of(),
        (a, c) -> {
          calls.incrementAndGet();
          return Set.of();
        });
    Artifact.Context context = context(other, new Fixtures.Retention(), Path.of(""), Map.of());
    assertThrows(IllegalArgumentException.class, () -> artifact.images(context));
    assertEquals(0, calls.get());
    assertEquals(0, source.opens);
  }

  @Test
  void checksLayoutsBeforeAcquisitionAndClosesOpenedStreamsOnFailure() throws Exception {
    Fixtures.Source source = new Fixtures.Source(new byte[] { 1, 2, 3 });
    Fixtures.Retention retention = new Fixtures.Retention();
    Artifact artifact = Fixtures.artifact(Mod.class, false, ID, "Mod", source, Set.of(FABRIC), Set.of(), Set.of(),
        null);
    Artifact.Context bad = context(FABRIC, retention, Path.of("../outside"), Map.of());
    assertThrows(IllegalArgumentException.class, () -> artifact.images(bad));
    assertEquals(0, source.opens);
    assertEquals(0, retention.opens);
    retention.failAt = 1;
    IOException captureFailure = assertThrows(IOException.class,
        () -> artifact.images(context(FABRIC, retention, Path.of(""),
            Map.of(ID, new Artifact.Layout.Root(false, Path.of("mods"))))));
    assertEquals("Injected capture failure.", captureFailure.getMessage());
    assertEquals(1, source.opens);
    assertEquals(1, source.streamCloses);
    assertEquals(0, source.storeCloses);
    assertEquals(0, retention.storeCloses);
  }

  @Test
  void preservesExistingInstallationTreeOnSuccessAndFailure() throws Exception {
    Path target = Files.createTempDirectory(Path.of("build"), "sdk-installation-");
    try {
      Files.createDirectories(target.resolve("mods"));
      Files.writeString(target.resolve("mods/example.bin"), "existing target bytes");
      Files.writeString(target.resolve("sentinel"), "untouched");
      Map<String, String> before = Fixtures.snapshot(target);
      Fixtures.Source source = new Fixtures.Source(new byte[] { 1, 2, 3 });
      Fixtures.Retention retention = new Fixtures.Retention();
      Mod mod = new Mod.Default(ID, "Mod", source, Set.of(FABRIC), Set.of(), Set.of());
      Artifact.Context context = context(FABRIC, retention, target,
          Map.of(ID, new Artifact.Layout.Root(false, Path.of("mods"))));
      Set<Image<?>> images = mod.images(context);
      assertEquals(target.resolve("mods/example.bin"), images.iterator().next().path());
      assertEquals(before, Fixtures.snapshot(target));
      retention.failAt = retention.captures + 1;
      assertThrows(IOException.class, () -> mod.images(context));
      assertEquals(before, Fixtures.snapshot(target));
    } finally {
      try (var paths = Files.walk(target)) {
        for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
          Files.delete(path);
      }
    }
  }

  @Test
  void resolvesWorldOverridesAndSeparatesDefaultPackRoots() throws IOException {
    byte[] zip = Fixtures.zip("pack.mcmeta", "{}", "data/a.json", "[]");
    Fixtures.Source source = new Fixtures.Source("pack.zip", zip);
    Fixtures.Retention retention = new Fixtures.Retention();
    var first = new Datapack.Default(ID, "First", source, Set.of(FABRIC), Set.of(), Set.of());
    var second = new Datapack.Default(Identity.create("second"), "Second", source, Set.of(FABRIC), Set.of(), Set.of());
    Artifact.Context context = context(FABRIC, retention, Path.of("den"), Map.of());
    var images = new java.util.ArrayList<>(first.images(context));
    images.addAll(second.images(context));
    assertEquals(4, Image.fence(images, Image.relative(context.contextRoot())).size());
    assertTrue(images.stream().allMatch(image -> image.path().startsWith(Path.of("den/worlds/main/datapacks"))));
    Artifact.Context override = context(FABRIC, retention, Path.of("den"),
        Map.of(ID, new Artifact.Layout.World(false, Path.of("datapacks"))));
    assertEquals(Path.of("den/worlds/main/datapacks/pack.zip"), first.images(override).iterator().next().path());
  }

  @Test
  void unpacksSelfContainedModpackWithoutInventingDependencies() throws IOException {
    byte[] zip = Fixtures.zip("mods/a.jar", "mod", "config/a.json", "{}");
    Fixtures.Source source = new Fixtures.Source("pack.zip", zip);
    var pack = new Modpack.Default(ID, "Pack", source, Set.of(FABRIC), Set.of(), Set.of());
    Set<Image<?>> images = pack.images(context(FABRIC, new Fixtures.Retention(), Path.of("den"),
        Map.of(ID, new Artifact.Layout.Root(true, Path.of("")))));
    assertEquals(Set.of(Path.of("den/mods/a.jar"), Path.of("den/config/a.json")),
        images.stream().map(Image::path).collect(java.util.stream.Collectors.toSet()));
    assertTrue(pack.depends().isEmpty());
  }

  @Test
  void fencesCustomImagesForCollisionsAndContextEscapes() {
    List<Set<Image<?>>> cases = List.of(
        Set.of(Image.create(Path.of("outside/file"), CONTENT)),
        Set.of(Image.create(Path.of("../file"), CONTENT)),
        Set.of(Image.create(Path.of("den/file"), CONTENT), Image.delete(Path.of("den/file"), CONTENT)),
        Set.of(Image.create(Path.of("den/file/child"), CONTENT), Image.create(Path.of("den/file"), CONTENT)));
    for (Set<Image<?>> images : cases) {
      Mod custom = new Mod.Custom(ID, "Custom", new Fixtures.Source(new byte[0]), Set.of(FABRIC), Set.of(), Set.of(),
          (a, c) -> images);
      assertThrows(IllegalArgumentException.class,
          () -> custom.images(context(FABRIC, new Fixtures.Retention(), Path.of("den"), Map.of())));
    }
  }

  @Test
  void createsArtifactsWithoutReferencesAndLetsSourcesVerifyAcquisition() throws IOException {
    Fixtures.Source source = new Fixtures.Source("provider.jar", new byte[] { 4, 5 });
    Mod artifact = new Mod.Default(ID, "Example", source, Set.of(FABRIC), Set.of(), Set.of());
    assertEquals(0, source.opens);
    assertTrue(
        Arrays.stream(Artifact.class.getMethods()).noneMatch(method -> method.getReturnType() == Reference.class));
    assertTrue(Arrays.stream(Mod.Default.class.getRecordComponents())
        .noneMatch(component -> component.getType() == Reference.class));
    Fixtures.Retention retained = new Fixtures.Retention();
    var context = context(FABRIC, retained, Path.of(""), Map.of(ID, new Artifact.Layout.Root(false, Path.of("mods"))));
    Image<?> image = artifact.images(context).iterator().next();
    assertEquals(Path.of("mods/provider.jar"), image.path());
    Reference captured = ((Image.File.Present) image.after()).reference();
    assertEquals(Reference.of("provider.jar", new byte[] { 4, 5 }), captured);
    source.openFailure = new IOException("Provider checksum verification failed");
    assertSame(source.openFailure, assertThrows(IOException.class, () -> artifact.images(context)));
  }

  @Test
  void propagatesSourceOpenAndCloseFailuresAndPreservesSuppressedFailure() {
    Fixtures.Source source = new Fixtures.Source(new byte[] { 1, 2, 3 });
    Fixtures.Retention retention = new Fixtures.Retention();
    Mod mod = new Mod.Default(ID, "Mod", source, Set.of(FABRIC), Set.of(), Set.of());
    Artifact.Context context = context(FABRIC, retention, Path.of(""),
        Map.of(ID, new Artifact.Layout.Root(false, Path.of("mods"))));
    source.openFailure = new IOException("open failed");
    assertSame(source.openFailure, assertThrows(IOException.class, () -> mod.images(context)));
    assertEquals(0, retention.captures);
    assertEquals(0, source.streamCloses);
    source.openFailure = null;
    source.closeFailure = new IOException("close failed");
    assertSame(source.closeFailure, assertThrows(IOException.class, () -> mod.images(context)));
    retention.failAt = retention.captures + 1;
    IOException failure = assertThrows(IOException.class, () -> mod.images(context));
    assertEquals("Injected capture failure.", failure.getMessage());
    assertArrayEquals(new Throwable[] { source.closeFailure }, failure.getSuppressed());
    assertEquals(0, source.storeCloses);
    assertEquals(0, retention.storeCloses);
  }
}
