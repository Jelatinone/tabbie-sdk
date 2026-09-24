package cat.tabbie.sdk.addon;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.merchant.Provider;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * A provider-owned catalog project with independently addressed builds and
 * tags. Implementations supply immutable, non-null collections, a non-blank
 * name, builds released under this addon's project coordinates, and distinct
 * build coordinates and tag identities; {@link #validate} checks these.
 * Discovery may produce a project with no builds; each published {@link Build}
 * is complete.
 */
public interface Addon {

  /**
   * Returns provider coordinates for this project.
   *
   * @return project coordinates
   */
  @NonNull
  Coordinate.Project coordinates();

  /**
   * Identifies the owning catalog provider.
   *
   * @return provider identity using the same marker as
   *         {@link Provider#providerId()}
   */
  default Identity<Provider<?, ?>> providerId() {
    return coordinates().providerId();
  }

  /**
   * Identifies this project independently of its builds, derived from the
   * project coordinates.
   *
   * @return stable addon identity
   */
  default Identity<Addon> addonId() {
    return Identity.create(coordinates().canonical());
  }

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
   * Checks copied constructor parameters before record fields are assigned.
   *
   * @param coordinates candidate project coordinates
   * @param addonName   candidate display name
   * @param builds      candidate builds
   * @param tags        candidate tags
   * @throws IllegalArgumentException when the name is blank, a build belongs to
   *                                  another project, or build coordinates or
   *                                  tag identities repeat
   */
  static void validate(
      @NonNull Coordinate.Project coordinates,
      @NonNull String addonName,
      @NonNull Set<Build> builds,
      @NonNull Set<Tag> tags) {
    if (addonName.isBlank()) {
      throw new IllegalArgumentException("An addon needs a non-blank name.");
    }
    if (!builds.stream().allMatch(build -> build.project().equals(coordinates))) {
      throw new IllegalArgumentException("Every build must be a release of this addon's project.");
    }
    if (builds.stream().map(Build::coordinates).distinct().count() != builds.size()) {
      throw new IllegalArgumentException("Addon builds must have distinct coordinates.");
    }
    if (tags.stream().map(Tag::tagId).distinct().count() != tags.size()) {
      throw new IllegalArgumentException("Addon tags must have distinct identities.");
    }
  }

  /**
   * A published selection of artifacts. Every artifact must be a file of this
   * build and explicitly support every build label, while artifacts may declare
   * additional supported targets. External dependency availability is resolved
   * later.
   *
   * @param coordinates   exact release coordinates
   * @param releaseName   non-blank display name
   * @param releaseDate   publication instant
   * @param releaseNumber positive provider-assigned release number
   * @param labels        nonempty targets advertised for the entire build
   * @param content       nonempty immutable artifact selection with distinct
   *                      file coordinates
   */
  record Build(
      @NonNull Coordinate.Build coordinates,
      @NonNull String releaseName,
      @NonNull Instant releaseDate,
      long releaseNumber,
      @NonNull Set<Label> labels,
      @NonNull Set<Artifact> content) implements Release<Artifact> {

    /**
     * Copies and checks the published artifact selection and advertised labels.
     *
     * @throws IllegalArgumentException when publication invariants are violated
     */
    public Build {
      labels = Set.copyOf(labels);
      content = Release.validate(coordinates, releaseName, releaseNumber, content);
      validate(labels, content);
    }

    /**
     * Assesses artifact type support and this build's explicit declaration.
     * A structurally unsupported artifact takes precedence over missing evidence.
     *
     * @param target selected runtime target
     * @return declared support, structural rejection, or unknown support
     */
    public Compatibility compatibility(@NonNull Label target) {
      Set<Compatibility> assessments = content().stream()
          .map(target::compatibility)
          .collect(Collectors.toSet());
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
     * Payload ownership and distinct file coordinates are checked by
     * {@link Release#validate(Coordinate.Build, String, long, Set)}.
     *
     * @param labels    advertised targets
     * @param artifacts directly bundled artifacts
     */
    private static void validate(@NonNull Set<Label> labels, @NonNull Set<Artifact> artifacts) {
      if (labels.isEmpty()) {
        throw new IllegalArgumentException("A build needs supported labels.");
      }
      if (artifacts.stream().anyMatch(artifact -> artifact.conflicts().stream()
          .anyMatch(conflict -> artifacts.stream().anyMatch(other -> conflict.includes(other.coordinates()))))) {
        throw new IllegalArgumentException("Build artifacts conflict with each other.");
      }

      if (artifacts.stream().anyMatch(artifact -> labels.stream()
          .anyMatch(label -> label.compatibility(artifact) != Compatibility.SUPPORTED))) {
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
