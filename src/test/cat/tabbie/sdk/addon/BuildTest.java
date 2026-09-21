package cat.tabbie.sdk.addon;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import cat.tabbie.sdk.Fixtures;
import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Distribution;
import cat.tabbie.sdk.minecraft.Label;

class BuildTest {
  static final Label FABRIC = Fixtures.label(Distribution.Java.Of.FABRIC);
  static final Label QUILT = Fixtures.label(Distribution.Java.Of.QUILT);
  static final Identity<Artifact> ID = Identity.create("provider:addon:build:artifact");
  static Mod mod(Identity<Artifact> id, String name, Set<Label> labels, Set<Identity<Artifact>> conflicts) {
    return new Mod.Default(id, name, new Fixtures.Source(new byte[0]), labels, Set.of(), conflicts);
  }
  static Addon.Build build(Set<Label> labels, Set<Artifact> artifacts) {
    return new Addon.Build(Identity.create("addon"), Identity.create("build"), "Build", Instant.EPOCH, 1, labels, artifacts);
  }

  @Test void requiresNonemptySelectionsAndExplicitSupportByEveryArtifact() {
    Mod first = mod(ID, "First", Set.of(FABRIC, QUILT), Set.of());
    Mod second = mod(Identity.create("second"), "Second", Set.of(FABRIC), Set.of());
    assertThrows(IllegalArgumentException.class, () -> build(Set.of(), Set.of(first)));
    assertThrows(IllegalArgumentException.class, () -> build(Set.of(FABRIC), Set.of()));
    assertThrows(IllegalArgumentException.class, () -> build(Set.of(FABRIC, QUILT), Set.of(first, second)));
    assertThrows(IllegalArgumentException.class, () -> build(Set.of(Fixtures.label(Distribution.Java.Of.PAPER)), Set.of(first)));
    assertEquals(Compatibility.SUPPORTED, build(Set.of(FABRIC), Set.of(first, second)).compatibility(FABRIC));
    assertEquals(Compatibility.UNKNOWN, build(Set.of(FABRIC), Set.of(first, second)).compatibility(QUILT));
    assertEquals(Compatibility.UNKNOWN, build(Set.of(FABRIC), Set.of(first)).compatibility(QUILT));
    assertEquals(Compatibility.UNSUPPORTED, build(Set.of(FABRIC), Set.of(first)).compatibility(Fixtures.label(Distribution.Java.Of.PAPER)));
  }

  @Test void rejectsDuplicateIdsAndOneSidedConflicts() {
    Mod first = mod(ID, "First", Set.of(FABRIC), Set.of());
    Mod duplicate = mod(ID, "Duplicate", Set.of(FABRIC), Set.of());
    Mod conflict = mod(Identity.create("other"), "Conflict", Set.of(FABRIC), Set.of(ID));
    assertThrows(IllegalArgumentException.class, () -> build(Set.of(FABRIC), Set.of(first, duplicate)));
    assertThrows(IllegalArgumentException.class, () -> build(Set.of(FABRIC), Set.of(first, conflict)));
  }

  @Test void copiesPublishedInputsAndRejectsInvalidPublicationMetadata() {
    Set<Label> labels = new HashSet<>(Set.of(FABRIC, QUILT));
    Mod first = mod(ID, "First", labels, Set.of());
    Set<Artifact> artifacts = new HashSet<>(Set.of(first));
    Addon.Build build = build(labels, artifacts);
    labels.clear(); artifacts.clear();
    assertEquals(2, build.labels().size());
    assertEquals(Set.of(first), build.artifacts());
    assertThrows(UnsupportedOperationException.class, () -> build.artifacts().clear());
    assertThrows(UnsupportedOperationException.class, () -> build.labels().clear());
    assertThrows(IllegalArgumentException.class, () -> new Addon.Build(build.addonId(), build.buildId(), " ", Instant.EPOCH, 1, build.labels(), build.artifacts()));
    assertThrows(IllegalArgumentException.class, () -> new Addon.Build(build.addonId(), build.buildId(), "Name", Instant.EPOCH, 0, build.labels(), build.artifacts()));
    assertThrows(IllegalArgumentException.class, () -> new Addon.Tag(Identity.create("tag"), " "));
  }
}
