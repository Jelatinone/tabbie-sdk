package cat.tabbie.sdk.addon;

import java.time.Instant;
import java.util.Optional;
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
 * A provider-owned catalog project with independently addressed builds.
 * Implementations supply immutable, non-null collections, a non-blank name,
 * builds released under this addon's project coordinates, and distinct build
 * coordinates; {@link #validate} checks these. Discovery may produce a project
 * with no builds; each published {@link Build} is complete.
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
   * Exposes the most recent build declaration owned by this addon.
   *
   * @return build with a distinct identity, maybe empty.
   */
  @NonNull
  default Optional<Build> latest() {
    return builds().stream().max(Release.PUBLICATION_ORDER);
  }

  /**
   * Checks copied constructor parameters before record fields are assigned.
   *
   * @param coordinates candidate project coordinates
   * @param addonName   candidate display name
   * @param builds      candidate builds
   * @throws IllegalArgumentException when the name is blank, a build belongs to
   *                                  another project, or build coordinates
   *                                  repeat
   */
  static void validate(
      @NonNull Coordinate.Project coordinates,
      @NonNull String addonName,
      @NonNull Set<Build> builds) {
    if (addonName.isBlank()) {
      throw new IllegalArgumentException("An addon needs a non-blank name.");
    }
    if (!builds.stream().allMatch(build -> build.project().equals(coordinates))) {
      throw new IllegalArgumentException("Every build must be a release of this addon's project.");
    }
    if (builds.stream().map(Build::coordinates).distinct().count() != builds.size()) {
      throw new IllegalArgumentException("Addon builds must have distinct coordinates.");
    }
  }

  /**
   * A published release of one fixed set of artifacts, installed together for
   * every supported label. External dependency availability is resolved later.
   *
   * @param coordinates   exact release coordinates
   * @param releaseName   non-blank display name
   * @param releaseDate   publication instant
   * @param releaseNumber positive provider-assigned release number
   * @param content       supported labels and the artifacts, each a distinct
   *                      file of this release
   */
  record Build(
      @NonNull Coordinate.Build coordinates,
      @NonNull String releaseName,
      @NonNull Instant releaseDate,
      long releaseNumber,
      @NonNull Content content) implements Release<Build.Content> {

    /**
     * Checks shared release invariants and that every artifact is a distinct
     * file of this release.
     *
     * @throws IllegalArgumentException when publication invariants are violated
     */
    public Build {
      Release.validate(releaseName, releaseNumber);
      Release.Payload.validate(coordinates, content.artifacts());
    }

    /**
     * Creates a build installing every artifact for every label.
     *
     * @param coordinates   exact release coordinates
     * @param releaseName   non-blank display name
     * @param releaseDate   publication instant
     * @param releaseNumber positive provider-assigned release number
     * @param labels        nonempty targets advertised for the entire build
     * @param artifacts     nonempty artifacts, each supporting every label
     * @return build
     */
    public static Build of(
        @NonNull Coordinate.Build coordinates,
        @NonNull String releaseName,
        @NonNull Instant releaseDate,
        long releaseNumber,
        @NonNull Set<Label> labels,
        @NonNull Set<Artifact> artifacts) {
      return new Build(coordinates, releaseName, releaseDate, releaseNumber,
          new Content(labels, artifacts));
    }

    /**
     * Assesses artifact type support and this build's explicit declaration.
     * A structurally unsupported artifact takes precedence over missing evidence.
     *
     * @param target selected runtime target
     * @return declared support, structural rejection, or unknown support
     */
    public Compatibility compatibility(@NonNull Label target) {
      Set<Compatibility> assessments = content.artifacts().stream()
          .map(target::compatibility)
          .collect(Collectors.toSet());
      if (assessments.contains(Compatibility.UNSUPPORTED)) {
        return Compatibility.UNSUPPORTED;
      }
      return content.labels().stream().anyMatch(label -> label.match(target))
          && assessments.stream().allMatch(result -> result == Compatibility.SUPPORTED)
              ? Compatibility.SUPPORTED
              : Compatibility.UNKNOWN;
    }

    /**
     * The artifacts of a build and the targets they support together. Every
     * artifact must explicitly support every label, while artifacts may
     * declare additional supported targets.
     *
     * @param labels    nonempty targets advertised for the entire build
     * @param artifacts nonempty artifacts installed together
     */
    public record Content(@NonNull Set<Label> labels, @NonNull Set<Artifact> artifacts) {

      /**
       * Copies and checks the advertised labels and artifacts.
       *
       * @throws IllegalArgumentException when labels or artifacts are empty, an
       *                                  artifact does not support every label,
       *                                  or artifacts conflict with each other
       */
      public Content {
        labels = Set.copyOf(labels);
        artifacts = Set.copyOf(artifacts);
        if (labels.isEmpty() || artifacts.isEmpty()) {
          throw new IllegalArgumentException("A build needs supported labels and artifacts.");
        }
        Set<Artifact> selected = artifacts;
        if (selected.stream().anyMatch(artifact -> artifact.conflicts().stream()
            .anyMatch(conflict -> selected.stream().anyMatch(other -> conflict.includes(other.coordinates()))))) {
          throw new IllegalArgumentException("Build artifacts conflict with each other.");
        }
        Set<Label> declared = labels;
        if (selected.stream().anyMatch(artifact -> declared.stream()
            .anyMatch(label -> label.compatibility(artifact) != Compatibility.SUPPORTED))) {
          throw new IllegalArgumentException("Every build artifact must support every declared label.");
        }
      }
    }
  }
}
