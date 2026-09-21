package cat.tabbie.sdk.minecraft;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.Fixtures;
import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Mod;

class MatchingTest {
  static Stream<Distribution> aliases() {
    return Stream.concat(Stream.of(Distribution.Java.Of.values()), Stream.of(Distribution.Bedrock.Of.values()));
  }

  @ParameterizedTest @MethodSource("aliases")
  void checksEveryFamilyAgainstEveryAliasAndItsConcreteRecord(Distribution alias) throws Exception {
    Distribution concrete = alias instanceof Distribution.Java.Of java ? java.distribution
        : ((Distribution.Bedrock.Of) alias).distribution;
    assertNotEquals(alias, concrete);
    assertEquals(alias.id(), concrete.id());
    assertEquals(alias.capabilities(), concrete.capabilities());
    assertEquals(alias.environments(), concrete.environments());
    assertFalse(Fixtures.label(alias).match(Fixtures.label(concrete)));
    assertEquals(concrete, concrete.getClass().getConstructor().newInstance());
    assertThrows(UnsupportedOperationException.class, () -> alias.capabilities().clear());
    assertThrows(UnsupportedOperationException.class, () -> alias.environments().clear());
    for (Class<? extends Artifact> family : Fixtures.families().toList()) {
      boolean supported = alias.capabilities().contains(family);
      for (boolean custom : new boolean[]{false, true}) {
        Artifact artifact = Fixtures.artifact(family, custom, Identity.create("matrix:" + family + custom), "Example",
            new Fixtures.Source(new byte[0]),
            Set.of(Fixtures.target(family)), Set.of(), Set.of(), (a, c) -> Set.of());
        assertEquals(supported, alias.supports(family));
        assertEquals(supported, alias.supports(artifact.getClass()));
        if (!supported) {
          assertEquals(Compatibility.UNSUPPORTED, Fixtures.label(alias).compatibility(artifact));
          assertThrows(IllegalArgumentException.class, () -> Fixtures.artifact(family, custom, artifact.artifactId(), "Unsupported",
              new Fixtures.Source(new byte[0]), Set.of(Fixtures.label(alias)), Set.of(), Set.of(), (a, c) -> Set.of()));
          continue;
        }
        Artifact declared = Fixtures.artifact(family, custom, artifact.artifactId(), "Declared",
            new Fixtures.Source(new byte[0]), Set.of(Fixtures.label(alias)), Set.of(), Set.of(), (a, c) -> Set.of());
        assertEquals(Compatibility.SUPPORTED, Fixtures.label(alias).compatibility(declared));
        var context = new Artifact.Context.Default(Fixtures.label(alias), new Fixtures.Retention(), Path.of(""), Path.of("world"));
        Artifact.Layout expected;
        try {
          expected = concrete.layout(declared, context);
        } catch (IOException noDefault) {
          assertThrows(IOException.class, () -> alias.layout(declared, context));
          continue;
        }
        assertEquals(expected, alias.layout(declared, context));
      }
    }
  }

  @Test void versionAndLabelMatchingIgnoreReleaseMetadataButPreserveEditionAndDistribution() throws Exception {
    Version a = new Version.Java(" 1.21-RC1 ", Version.Java.Release.RELEASE_CANDIDATE, Instant.EPOCH);
    Version b = new Version.Java("1.21-rc1", Version.Java.Release.UNKNOWN, Instant.ofEpochSecond(10));
    Version bedrock = new Version.Bedrock("1.21-rc1", Version.Bedrock.Release.RELEASE, Instant.EPOCH);
    assertNotEquals(a, b);
    var match = assertDoesNotThrow(() -> Version.class.getMethod("match", Version.class));
    assertEquals(true, match.invoke(a, b));
    assertEquals(false, match.invoke(a, bedrock));
    assertEquals(true, match.invoke(bedrock, new Version.Bedrock("1.21-RC1", Version.Bedrock.Release.UNKNOWN, Instant.now())));
    assertTrue(new Label(a, new Distribution.Java.Fabric(), Environment.SERVER).match(new Label(b, new Distribution.Java.Fabric(), Environment.SERVER)));
    assertFalse(new Label(a, Distribution.Java.Of.FABRIC, Environment.SERVER).match(new Label(b, new Distribution.Java.Fabric(), Environment.SERVER)));
    assertFalse(new Label(a, Distribution.Java.Of.FABRIC, Environment.CLIENT).match(new Label(b, Distribution.Java.Of.FABRIC, Environment.SERVER)));
  }

  @Test void distinguishesAllCompatibilityOutcomesWithoutInferringForks() {
    Label fabric = Fixtures.label(Distribution.Java.Of.FABRIC);
    Mod mod = new Mod.Default(Identity.create("mod"), "Mod",
        new Fixtures.Source(new byte[0]), Set.of(fabric), Set.of(), Set.of());
    assertEquals(Compatibility.SUPPORTED, fabric.compatibility(mod));
    assertEquals(Compatibility.UNKNOWN, Fixtures.label(Distribution.Java.Of.QUILT).compatibility(mod));
    assertEquals(Compatibility.UNKNOWN, new Label(new Version.Java("unknown", Version.Java.Release.UNKNOWN, Instant.EPOCH),
        Distribution.Java.Of.FABRIC, Environment.SERVER).compatibility(mod));
    assertEquals(Compatibility.UNKNOWN, new Label(fabric.version(), Distribution.Java.Of.FABRIC, Environment.CLIENT).compatibility(mod));
    assertEquals(Compatibility.UNSUPPORTED, Fixtures.label(Distribution.Java.Of.PAPER).compatibility(mod));
    assertThrows(IllegalArgumentException.class, () -> new Label(fabric.version(), Distribution.Bedrock.Of.BEDROCK_NATIVE, Environment.SERVER));
    assertThrows(IllegalArgumentException.class, () -> new Label(fabric.version(), Distribution.Java.Of.PAPER, Environment.CLIENT));
  }

  @ParameterizedTest @ValueSource(strings = {"", " ", "a b", "minecraft:1.21", "a\tb", "a\u00a0b"})
  void rejectsInvalidVersionIdentifiers(String id) {
    assertThrows(IllegalArgumentException.class, () -> new Version.Java(id, Version.Java.Release.UNKNOWN, Instant.EPOCH));
    assertThrows(IllegalArgumentException.class, () -> new Version.Bedrock(id, Version.Bedrock.Release.UNKNOWN, Instant.EPOCH));
  }

  @Test void exposesImmutableEnumerationAndExactNamespacedLookup() throws Exception {
    var all = assertDoesNotThrow(() -> Distribution.class.getMethod("all"));
    var find = assertDoesNotThrow(() -> Distribution.class.getMethod("find", String.class));
    @SuppressWarnings("unchecked") List<Distribution> values = (List<Distribution>) all.invoke(null);
    assertEquals(aliases().toList(), values);
    assertEquals(values.size(), values.stream().map(Distribution::id).distinct().count());
    assertThrows(UnsupportedOperationException.class, values::clear);
    for (Distribution alias : values) assertEquals(java.util.Optional.of(alias), find.invoke(null, alias.id()));
    assertEquals(java.util.Optional.empty(), find.invoke(null, "JAVA:FABRIC"));
    assertEquals(java.util.Optional.empty(), find.invoke(null, "unknown"));
    assertEquals("bedrock:endstone", new Distribution.Bedrock.Endstone().id());
  }
}
