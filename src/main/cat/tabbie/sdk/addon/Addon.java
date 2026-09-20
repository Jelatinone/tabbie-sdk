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
 * A provider-owned catalog project with independently identified builds and
 * tags. Implementations supply immutable, non-null collections, a non-blank
 * name, builds owned by this addon, and distinct build/tag identities.
 * Discovery may produce a project with no builds; each published {@link Build}
 * is complete.
 */
public interface Addon {

  /**
   * Identifies the owning catalog provider.
   *
   * @return provider identity using the same marker as
   *         {@link Provider#providerId()}
   */
  @NonNull
  Identity<Provider<?>> providerId();

  /**
   * Identifies this project independently of its builds.
   * 
   * @return stable addon identity
   */
  @NonNull
  Identity<Addon> addonId();

  /**
   * Names this project for display.
   *
   * @return non-blank canonical name
   */
  @NonNull
  String addonName();

  /**
   * Exposes immutable build declarations owned by this addon.
   *
   * @return builds with distinct identities, possibly empty
   */
  @NonNull
  Set<Build> builds();

  /**
   * Exposes provider-defined catalog tags.
   * 
   * @return immutable tags with distinct identities, possibly empty
   */
  @NonNull
  Set<Tag> tags();

  /**
   * A published selection of artifacts. Every artifact must explicitly support
   * every build label, while artifacts may declare additional supported targets.
   * External dependency availability is resolved later.
   *
   * @param addonId     owning catalog addon
   * @param buildId     immutable build identity
   * @param buildName   non-blank display name
   * @param buildDate   publication instant
   * @param buildNumber positive provider-assigned build number
   * @param labels      nonempty targets advertised for the entire build
   * @param artifacts   nonempty immutable artifact selection with distinct
   *                    identities
   */
  record Build(
      @NonNull Identity<Addon> addonId,
      @NonNull Identity<Build> buildId,

      @NonNull String buildName,
      @NonNull Instant buildDate,
      long buildNumber,

      @NonNull Set<Label> labels,
      @NonNull Set<Artifact> artifacts) {

    /**
     * Copies and checks the published artifact selection and advertised labels.
     *
     * @throws IllegalArgumentException when publication invariants are violated
     */
    public Build {
      labels = Set.copyOf(labels);
      artifacts = Set.copyOf(artifacts);
      if (buildNumber < 1L || buildName.isBlank()) {
        throw new IllegalArgumentException("Build number must be positive and name non-blank.");
      }
      validate(labels, artifacts);
    }

    /**
     * Assesses artifact type support and this build's explicit declaration.
     * A structurally unsupported artifact takes precedence over missing evidence.
     *
     * @param target selected runtime target
     * @return declared support, structural rejection, or unknown support
     */
    public Compatibility compatibility(@NonNull Label target) {
      Set<Compatibility> assessments = artifacts.stream()
          .map(target::compatibility).collect(Collectors.toSet());
      if (assessments.contains(Compatibility.UNSUPPORTED)) {
        return Compatibility.UNSUPPORTED;
      }
      return labels.stream().anyMatch(label -> label.match(target))
          && assessments.stream().allMatch(result -> result == Compatibility.SUPPORTED)
              ? Compatibility.SUPPORTED
              : Compatibility.UNKNOWN;
    }

    /**
     * Checks copied constructor inputs before record fields are initialized.
     * 
     * @param labels    advertised targets
     * @param artifacts directly bundled artifacts
     */
    private static void validate(@NonNull Set<Label> labels, @NonNull Set<Artifact> artifacts) {
      if (labels.isEmpty() || artifacts.isEmpty()) {
        throw new IllegalArgumentException("A build needs supported labels and required artifacts.");
      }
      Set<Identity<Artifact>> ids = artifacts.stream()
          .map(Artifact::artifactId)
          .collect(Collectors.toSet());

      if (ids.size() != artifacts.size()) {
        throw new IllegalArgumentException("Build artifacts must have distinct identities.");
      }
      if (artifacts.stream().anyMatch(artifact -> artifact.conflicts().stream().anyMatch(ids::contains))) {
        throw new IllegalArgumentException("Build artifacts conflict with each other.");
      }

      if (artifacts.stream().anyMatch(artifact -> labels.stream()
          .anyMatch(label -> label.compatibility(artifact) == Compatibility.UNSUPPORTED))) {
        throw new IllegalArgumentException("Every build artifact must support every declared label.");
      }
    }
  }

  /**
   * Provider-defined immutable catalog tag.
   *
   * @param tagId   stable tag identity
   * @param tagName non-blank display name
   */
  record Tag(
      @NonNull Identity<Tag> tagId,
      @NonNull String tagName) {

    /**
     * Checks that a tag has a meaningful display name.
     *
     * @throws IllegalArgumentException when the name is blank
     */
    public Tag {
      if (tagName.isBlank()) {
        throw new IllegalArgumentException("Tag name must be non-blank");
      }
    }
  }

}
