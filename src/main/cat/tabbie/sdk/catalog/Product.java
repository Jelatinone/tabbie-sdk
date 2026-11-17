package cat.tabbie.sdk.catalog;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.distribution.Distributable;
import cat.tabbie.sdk.distribution.Distribution;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Label;
import lombok.NonNull;

/**
 * A provider-owned catalog project publishing releases of one distribution,
 * such as Paper's server builds or Fabric's loader. It mirrors
 * {@code Addon} for distributions. Implementations supply immutable, non-null
 * collections, a non-blank name, and builds released under this project's
 * coordinates for this project's distribution; {@link #validate} checks these.
 * Discovery may produce a project without builds.
 */
public interface Product {

  /**
   * Returns provider coordinates for this project.
   *
   * @return project coordinates
   */
  @NonNull
  Coordinate.Project coordinates();

  /**
   * Names this project for display.
   *
   * @return non-blank name
   */
  @NonNull
  String projectName();

  /**
   * Returns the distribution every release of this project runs as.
   *
   * @return distribution family value
   */
  @NonNull
  Distribution distribution();

  /**
   * Exposes the known releases of this project.
   *
   * @return immutable builds with distinct coordinates, possibly empty
   */
  @NonNull
  Set<Build> builds();

  /**
   * Exposes the most recent build of this project on any channel.
   *
   * @return latest build, or empty when no build is known
   */
  @NonNull
  default Optional<Build> latest() {
    return builds().stream()
        .max(Release.PUBLICATION_ORDER);
  }

  /**
   * Selects the most recent release on an accepted channel.
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
   * @param coordinates  candidate project coordinates
   * @param projectName  candidate display name
   * @param distribution candidate distribution
   * @param builds       candidate builds
   * @throws IllegalArgumentException when the name is blank, a build belongs to
   *                                  another project or serves another
   *                                  distribution, or build coordinates repeat
   */
  static void validate(
      @NonNull Coordinate.Project coordinates,
      @NonNull String projectName,
      @NonNull Distribution distribution,
      @NonNull Set<Build> builds) {
    if (projectName.isBlank()) {
      throw new IllegalArgumentException("A product needs a non-blank name.");
    }
    if (!builds.stream().allMatch(build -> build.project().equals(coordinates))) {
      throw new IllegalArgumentException("Every build must be a release of this product's project.");
    }
    if (!builds.stream().allMatch(build -> build.distribution().equals(distribution))) {
      throw new IllegalArgumentException("Every build must serve this product's distribution.");
    }
    if (builds.stream().map(Build::coordinates).distinct().count() != builds.size()) {
      throw new IllegalArgumentException("Product builds must have distinct coordinates.");
    }
  }

  /**
   * A published release of one distribution: its distributables, the system
   * packages they require, and every target it serves, as its provider
   * declares them. A release serves one environment, so a client build and a
   * server build of a distribution are separate releases, on channels of their
   * own. Where its content goes is not the release's to say: the
   * {@link cat.tabbie.sdk.meta.action.Place places} applying to its targets
   * say it, by version, and the
   * {@link cat.tabbie.sdk.meta.registry.Registry#check(Product.Build)
   * registry checks} they place everything the release holds and its
   * contributors act on.
   *
   * @param coordinates   exact release coordinates
   * @param releaseName   non-blank display name
   * @param releaseDate   publication instant
   * @param releaseNumber positive provider-assigned release number
   * @param content       nonempty distributables, each a distinct file of this
   *                      release
   * @param packages      system packages the distributables require
   * @param distribution  distribution the release is for
   * @param labels        nonempty targets the release serves, such as every
   *                      game version it runs, all of one environment
   */
  record Build(
      @NonNull Coordinate.Build coordinates,
      @NonNull String releaseName,
      @NonNull Instant releaseDate,
      long releaseNumber,
      @NonNull Set<Distributable> content,
      @NonNull Set<Package> packages,
      @NonNull Distribution distribution,
      @NonNull Set<Label> labels) implements Release<Distributable> {

    /**
     * Copies the distributables, packages and labels, then checks shared
     * release invariants, that every distributable is a distinct file of this
     * release, that the labels are of its distribution and of one environment,
     * and that the distributables serve exactly the labels, and so only its
     * distribution.
     *
     * @throws IllegalArgumentException when publication invariants are
     *                                  violated, no label is given, a label is
     *                                  of another distribution or environment,
     *                                  a distributable serves a target that is
     *                                  not a label, or a label is served by no
     *                                  distributable
     */
    public Build {
      content = Set.copyOf(content);
      packages = Set.copyOf(packages);
      labels = Set.copyOf(labels);
      Release.validate(releaseName, releaseNumber);
      Release.Payload.validate(coordinates, content);
      if (labels.isEmpty()) {
        throw new IllegalArgumentException("A distribution release needs at least one label.");
      }
      if (labels.stream().anyMatch(label -> !label.distribution().equals(distribution))) {
        throw new IllegalArgumentException("Every label of a release must be of its distribution.");
      }
      if (labels.stream().map(Label::environment).distinct().count() != 1) {
        throw new IllegalArgumentException("Every label of a release must be of one environment; a client build and "
            + "a server build are separate releases.");
      }
      Set<Label> served = labels;
      Set<Distributable> distributables = content;
      if (distributables.stream()
          .flatMap(distributable -> distributable.labels().stream())
          .anyMatch(target -> served.stream().noneMatch(target::match))) {
        throw new IllegalArgumentException("Every distributable target must be a label of its release.");
      }
      if (served.stream().anyMatch(label -> distributables.stream()
          .noneMatch(distributable -> distributable.labels().stream().anyMatch(label::match)))) {
        throw new IllegalArgumentException("Every label of a release must be served by one of its distributables.");
      }
    }

    /**
     * Returns the environment the release serves, which every label shares.
     *
     * @return environment of the labels
     */
    public Environment environment() {
      return labels.iterator().next().environment();
    }
  }
}
