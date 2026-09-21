package cat.tabbie.sdk.album;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import cat.tabbie.sdk.Fixtures;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Mod;

class PathTest {
  static final Reference BYTES = Reference.of("file", new byte[0]);

  @Test
  void resolvesRootAndWorldMountsAndNormalizesInternalSegments() throws Exception {
    var context = new Artifact.Context.Default(Fixtures.target(Mod.class), new Fixtures.Retention(),
        Path.of("den/a/.."), Path.of("worlds/main"));
    assertEquals(Path.of("den/mods"), context.resolve(new Artifact.Layout.Root(false, Path.of("mods"))));
    assertEquals(Path.of("den/worlds/main/datapacks"),
        context.resolve(new Artifact.Layout.World(true, Path.of("datapacks"))));
    assertEquals(Path.of(""), Image.relative(Path.of(".")));
    assertEquals(Path.of("mods"), Image.relative(Path.of("config/../mods")));
    assertThrows(IllegalArgumentException.class, () -> Image.relative(Path.of("").toAbsolutePath()));
    assertThrows(IllegalArgumentException.class,
        () -> new Artifact.Context.Default(context.label(), context.store(), Path.of("../escape"), Path.of("world")));
    assertThrows(IllegalArgumentException.class,
        () -> new Artifact.Context.Default(context.label(), context.store(), Path.of(""), Path.of("../escape")));
    assertThrows(NullPointerException.class,
        () -> new Artifact.Context.Default(null, context.store(), Path.of(""), Path.of("world")));
    assertThrows(NullPointerException.class,
        () -> new Artifact.Context.Default(context.label(), null, Path.of(""), Path.of("world")));
  }

  @ParameterizedTest
  @ValueSource(strings = { "../outside", "a/../../outside" })
  void rejectsEscapesForEveryLayoutScope(String path) {
    assertThrows(IllegalArgumentException.class, () -> Image.relative(Path.of(path)));
    assertThrows(IllegalArgumentException.class, () -> new Artifact.Layout.Root(false, Path.of(path)));
    assertThrows(IllegalArgumentException.class, () -> new Artifact.Layout.World(false, Path.of(path)));
  }

  @Test
  void fencesRelativeFilesAndDetectsDuplicatesAndAncestorCollisions() {
    Set<Image<?>> valid = Image
        .fence(List.of(Image.create(Path.of("root/file"), BYTES), Image.create(Path.of("world/file"), BYTES)),
            Image.relative(Path.of("root/")));
    assertEquals(2, valid.size());
    assertThrows(UnsupportedOperationException.class, valid::clear);
    for (List<Image<Image.File>> invalid : List.of(
        List.of(Image.create(Path.of("a/../file"), BYTES)),
        List.of(Image.create(Path.of(""), BYTES)),
        List.of(Image.create(Path.of("file").toAbsolutePath(), BYTES)),
        List.of(Image.create(Path.of("file"), BYTES), Image.create(Path.of("file"), BYTES)),
        List.of(Image.create(Path.of("file/child"), BYTES), Image.create(Path.of("file"), BYTES)),
        List.of(Image.create(Path.of("file"), BYTES), Image.create(Path.of("file/child"), BYTES)))) {
      assertThrows(IllegalArgumentException.class, () -> Image.fence(invalid, Image.relative(Path.of("file"))));
    }
    assertThrows(NullPointerException.class,
        () -> Image.fence(java.util.Arrays.asList((Image<?>) null), Image.relative(Path.of("root"))));
  }

  @Test
  void roundTripsCanonicalPortablePaths() throws Exception {
    var encode = assertDoesNotThrow(() -> Image.class.getMethod("encode", Path.class));
    var decode = assertDoesNotThrow(() -> Image.class.getMethod("decode", String.class));
    Path path = Path.of("worlds", "main", "datapacks", "file.zip");
    assertEquals("worlds/main/datapacks/file.zip", encode.invoke(null, path));
    assertEquals(path, decode.invoke(null, encode.invoke(null, path)));
    for (String invalid : List.of("", "../outside", "/absolute", "C:drive", "a\\b", "a//b", "a/./b", "a/../b")) {
      assertInstanceOf(IllegalArgumentException.class,
          assertThrows(java.lang.reflect.InvocationTargetException.class, () -> decode.invoke(null, invalid))
              .getCause());
    }
  }
}
