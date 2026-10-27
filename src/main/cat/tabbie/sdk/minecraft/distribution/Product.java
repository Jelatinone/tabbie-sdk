package cat.tabbie.sdk.minecraft.distribution;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.merchant.Provider;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.platform.layout.Node;
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
  Provider.Coordinate.Project coordinates();

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
  default Optional<Build> latest(@NonNull Set<Provider.Coordinate.Channel> channels) {
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
   * A published release of one distribution: its byproducts; the system
   * packages they require; every target it serves; and the layout of a Cat
   * running it, as its provider declares it. The release's content is the
   * same whichever of its targets a Cat selects, so one layout serves them
   * all. Where content goes can change between releases, so a Cat moving to
   * another release mounts that release's layout instead.
   *
   * @param coordinates   exact release coordinates
   * @param releaseName   non-blank display name
   * @param releaseDate   publication instant
   * @param releaseNumber positive provider-assigned release number
   * @param content       nonempty byproducts, each a distinct file of this
   *                      release
   * @param packages      system packages the byproducts require
   * @param distribution  distribution the release is for
   * @param labels        nonempty targets the release serves, such as every
   *                      game version it runs
   * @param layout        layout of a Cat running this release, declared from
   *                      the root
   */
  record Build(
      @NonNull Coordinate.Build coordinates,
      @NonNull String releaseName,
      @NonNull Instant releaseDate,
      long releaseNumber,
      @NonNull Set<Byproduct> content,
      @NonNull Set<Package> packages,
      @NonNull Distribution distribution,
      @NonNull Set<Label> labels,
      @NonNull Node.Declared.Directory layout) implements Release<Byproduct> {

    /**
     * Copies the byproducts, packages and labels, then checks shared release
     * invariants, that every byproduct is a distinct file of this release,
     * that the labels are of its distribution, that the byproducts serve
     * exactly the labels, and so only its distribution, and that the layout
     * is declared from the root.
     *
     * @throws IllegalArgumentException when publication invariants are violated,
     *                                  no label is given, a label is of
     *                                  another distribution, a byproduct
     *                                  serves a target that is not a label, a
     *                                  label is served by no byproduct, or the
     *                                  layout is not declared from the root
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
      Set<Label> served = labels;
      Set<Byproduct> byproducts = content;
      if (byproducts.stream()
          .flatMap(byproduct -> byproduct.labels().stream())
          .anyMatch(target -> served.stream().noneMatch(target::match))) {
        throw new IllegalArgumentException("Every byproduct target must be a label of its release.");
      }
      if (served.stream().anyMatch(label -> byproducts.stream()
          .noneMatch(byproduct -> byproduct.labels().stream().anyMatch(label::match)))) {
        throw new IllegalArgumentException("Every label of a release must be served by one of its byproducts.");
      }
      if (!layout.address().isRoot()) {
        throw new IllegalArgumentException("A layout is declared from the root.");
      }
    }
  }
}
