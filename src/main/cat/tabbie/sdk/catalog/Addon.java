package cat.tabbie.sdk.catalog;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.content.Artifact;
import cat.tabbie.sdk.target.Label;
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
   * Exposes the most recent build owned by this addon on any channel.
   *
   * @return latest build, or empty when no build is known
   */
  @NonNull
  default Optional<Build> latest() {
    return builds().stream().max(Release.PUBLICATION_ORDER);
  }

  /**
   * Exposes the most recent build on an accepted channel, such as the latest
   * stable build for an update.
   *
   * @param channels accepted channels
   * @return latest accepted build, or empty when none is known
   */
  @NonNull
  default Optional<Build> latest(@NonNull Set<Coordinate.Channel> channels) {
    return builds().stream()
        .filter(build -> channels.contains(build.coordinates().channel()))
        .max(Release.PUBLICATION_ORDER);
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
   * every advertised label. Every artifact explicitly supports every label and
   * may declare further targets of its own. External dependency availability is
   * resolved later.
   *
   * @param coordinates   exact release coordinates
   * @param releaseName   non-blank display name
   * @param releaseDate   publication instant
   * @param releaseNumber positive provider-assigned release number
   * @param labels        nonempty targets advertised for the entire build
   * @param content       nonempty artifacts, each a distinct file of this
   *                      release
   */
  record Build(
      @NonNull Coordinate.Build coordinates,
      @NonNull String releaseName,
      @NonNull Instant releaseDate,
      long releaseNumber,
      @NonNull Set<Label> labels,
      @NonNull Set<Artifact> content) implements Release<Artifact> {

    /**
     * Copies the labels and artifacts, then checks shared release invariants,
     * that every artifact is a distinct file of this release declaring every
     * label, and that no artifact conflicts with another of the same build.
     * Whether each label supports each artifact's family depends on the
     * installs core has loaded, so
     * {@link Artifact.Registry#compatibility(Addon.Build, Label)} assesses it.
     *
     * @throws IllegalArgumentException when publication invariants are violated,
     *                                  no label is advertised, an artifact does
     *                                  not declare every label, or artifacts
     *                                  conflict with each other
     */
    public Build {
      labels = Set.copyOf(labels);
      content = Set.copyOf(content);
      Release.validate(releaseName, releaseNumber);
      Release.Payload.validate(coordinates, content);
      if (labels.isEmpty()) {
        throw new IllegalArgumentException("A build needs at least one advertised label.");
      }
      Set<Label> advertised = labels;
      Set<Artifact> artifacts = content;
      if (artifacts.stream().anyMatch(artifact -> advertised.stream()
          .anyMatch(label -> artifact.labels().stream().noneMatch(label::match)))) {
        throw new IllegalArgumentException("Every build artifact must declare every advertised label.");
      }
      if (artifacts.stream().anyMatch(artifact -> artifact.conflicts().stream()
          .anyMatch(conflict -> artifacts.stream().anyMatch(other -> conflict.includes(other.coordinates()))))) {
        throw new IllegalArgumentException("Build artifacts must not conflict with each other.");
      }
    }

    /**
     * Creates a build of a single artifact installed for every label.
     *
     * @param coordinates   exact release coordinates
     * @param releaseName   non-blank display name
     * @param releaseDate   publication instant
     * @param releaseNumber positive provider-assigned release number
     * @param labels        nonempty targets advertised for the entire build
     * @param content       artifact supporting every label
     * @return build
     * @throws IllegalArgumentException when publication invariants are violated
     */
    public static Build of(
        @NonNull Coordinate.Build coordinates,
        @NonNull String releaseName,
        @NonNull Instant releaseDate,
        long releaseNumber,
        @NonNull Set<Label> labels,
        @NonNull Artifact content) {
      return new Build(coordinates, releaseName, releaseDate, releaseNumber,
          labels, Set.of(content));
    }
  }
}
