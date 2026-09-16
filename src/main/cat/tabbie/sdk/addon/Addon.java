package cat.tabbie.sdk.addon;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 *
 * <h1>Addon</h1>
 *
 * <p>
 * </p>
 *
 */
public interface Addon {

  /**
   *
   * @return
   */
  @NonNull
  Identity<Provider<?>> providerId();

  /**
   *
   * @return
   */
  @NonNull
  Identity<Addon> addonId();

  /**
   *
   * @return
   */
  @NonNull
  String addonName();

  /**
   *
   * @return
   */
  Set<Build> builds();

  /**
   *
   * @return
   */
  Set<Tag> tags();

  record Build(
      @NonNull Identity<Addon> addonId,
      @NonNull Identity<Build> buildId,

      @NonNull String buildName,
      @NonNull Instant buildDate,
      long buildNumber,

      @NonNull Set<Label> labels,
      @NonNull Set<Artifact> artifacts) {

    public Build {
      labels = Set.copyOf(labels);
      artifacts = Set.copyOf(artifacts);

      Set<Identity<Artifact>> artifactIds = artifacts().stream()
          .map(Artifact::artifactId)
          .collect(Collectors.toSet());

      if (buildNumber < 1L) {
        throw new IllegalArgumentException("Build number must be positive!");
      }
      if (buildName.isBlank()) {
        throw new IllegalArgumentException("Build name must be non-blank");
      }
      if (artifactIds.size() != artifacts().size()) {
        throw new IllegalArgumentException("Build artifacts must be distinct");
      }
      if (artifacts.stream().anyMatch(artifact -> artifact.conflicts().stream().anyMatch(artifactIds::contains))) {
        throw new IllegalArgumentException("Build artifacts conflict with each other");
      }
      if (!artifacts.stream().anyMatch(
          artifact -> labels().stream().anyMatch(label -> artifact.allow(label) != Compatibility.UNSUPPORTED))) {
        throw new IllegalArgumentException("Build artifacts must support every declared label");
      }
    }
  }

  record Tag(
      @NonNull Identity<Tag> tagId,
      @NonNull String tagName) {

    public Tag {
      if (tagName.isBlank()) {
        throw new IllegalArgumentException("Tag name must be non-blank");
      }
    }
  }

}
