package cat.tabbie.sdk.content;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.catalog.Addon;
import cat.tabbie.sdk.catalog.Coordinate;
import cat.tabbie.sdk.catalog.Identity;
import cat.tabbie.sdk.catalog.Release;
import cat.tabbie.sdk.distribution.Distribution;
import cat.tabbie.sdk.meta.action.Install;
import cat.tabbie.sdk.meta.registry.Contributions;
import cat.tabbie.sdk.meta.registry.Registrable;
import cat.tabbie.sdk.target.Compatibility;
import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Label;
import cat.tabbie.sdk.target.Parity;
import cat.tabbie.sdk.tree.Shape;
import lombok.NonNull;

/**
 * Provider-described content. How it is placed is contributed for its family
 * by {@link cat.tabbie.sdk.meta.action.Install} methods, which receive the
 * artifact's source at their head and describe changes without writing to the
 * Cat's files; a {@link Registry} chooses the install that applies to a
 * target, and what a distribution supports follows from them. File
 * coordinates identify the exact provider-scoped payload; relations name
 * catalog coordinates at any level, matched by
 * {@link Coordinate#includes(Coordinate)}.
 */
@Registrable(Registrable.Kind.TYPES)
public interface Artifact extends Release.Payload {

  /**
   * Returns what every content family declares about an artifact, so a
   * family's own record holds only what is its alone.
   *
   * @apiNote The coordinates, name, source, targets, parity and relations
   *          below are read from these details.
   * @return checked artifact details
   */
  @NonNull
  Details details();

  /**
   * Stable artifact identity derived from the file coordinates, so equal
   * coordinates always yield the same identity across processes.
   *
   * @return artifact identity
   */
  default Identity<Artifact> artifactId() {
    return Identity.create(coordinates().canonical());
  }

  @Override
  default Coordinate.File coordinates() {
    return details().coordinates();
  }

  /**
   * Human-readable canonical artifact name
   *
   * @return artifact name
   */
  @NonNull
  default String artifactName() {
    return details().artifactName();
  }

  /**
   * Returns this artifact's content, as its provider declares it.
   *
   * @return the content's file
   */
  @NonNull
  default Shape.File source() {
    return details().source();
  }

  /**
   * Declares support for the entire artifact.
   *
   * @return immutable, nonempty supported targets
   */
  @NonNull
  default Set<Label> labels() {
    return details().labels();
  }

  /**
   * Declares which multiplayer sides this artifact runs on and what it
   * requires of the other side.
   *
   * @return side parity, {@link Parity#UNKNOWN} without evidence
   */
  @NonNull
  default Parity parity() {
    return details().parity();
  }

  /**
   * Catalog relationships with other content, at most one per coordinate.
   *
   * @return immutable relations
   */
  @NonNull
  default Set<Relation> relations() {
    return details().relations();
  }

  /**
   * Catalog content that must be installed alongside this artifact. A project
   * coordinate is satisfied by any file of that project, a build coordinate by
   * any file of that release, and a file coordinate only by that exact file.
   *
   * @return required coordinates
   */
  default Set<Coordinate> depends() {
    return coordinates(Relation.Required.class);
  }

  /**
   * Catalog content that may not be installed alongside this artifact, matched
   * the same way as {@link #depends()}.
   *
   * @return conflicting coordinates
   */
  default Set<Coordinate> conflicts() {
    return coordinates(Relation.Incompatible.class);
  }

  /**
   * Selects the coordinates of one kind of relation.
   *
   * @param kind relation kind, such as {@code Relation.Required.class}
   * @return immutable coordinates of that kind
   */
  default Set<Coordinate> coordinates(@NonNull Class<? extends Relation> kind) {
    return relations().stream()
        .filter(relation -> relation.getClass() == kind)
        .map(Relation::coordinate)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * What every content family declares about an artifact, as its provider
   * describes it. Whether a declared target supports the artifact's family
   * depends on the installs core has loaded, so it is not checked here: core
   * decides it while resolving, through
   * {@link Registry#compatibility(Artifact, Label)}.
   *
   * @param coordinates  exact provider file coordinates
   * @param artifactName non-blank display name
   * @param source       the content's file, as its provider declares it
   * @param labels       nonempty supported targets
   * @param parity       multiplayer side parity
   * @param relations    catalog relationships, one per coordinate
   */
  record Details(
      @NonNull Coordinate.File coordinates,
      @NonNull String artifactName,
      @NonNull Shape.File source,
      @NonNull Set<Label> labels,
      @NonNull Parity parity,
      @NonNull Set<Relation> relations) {

    /**
     * Copies the sets and checks the declarations without reading content.
     *
     * @throws IllegalArgumentException when the name is blank, no target is
     *                                  declared, a relation includes this
     *                                  artifact, a coordinate has two
     *                                  relations, or an incompatibility
     *                                  includes another relation
     */
    public Details {
      labels = Set.copyOf(labels);
      relations = Set.copyOf(relations);
      if (artifactName.isBlank() || labels.isEmpty()) {
        throw new IllegalArgumentException("An artifact needs a non-blank name and supported labels.");
      }
      if (relations.stream().anyMatch(relation -> relation.coordinate().includes(coordinates))) {
        throw new IllegalArgumentException("Relations must not reference this artifact.");
      }
      if (relations.stream().map(Relation::coordinate).distinct().count() != relations.size()) {
        throw new IllegalArgumentException("A coordinate may have only one relation.");
      }
      List<Coordinate> compatible = relations.stream()
          .filter(relation -> !(relation instanceof Relation.Incompatible))
          .map(Relation::coordinate)
          .toList();
      if (relations.stream()
          .filter(relation -> relation instanceof Relation.Incompatible)
          .anyMatch(conflict -> compatible.stream().anyMatch(other -> conflict.coordinate().includes(other)))) {
        throw new IllegalArgumentException("An incompatibility must not include another relation.");
      }
    }
  }

  /**
   * A catalog relationship with other content, matched by
   * {@link Coordinate#includes(Coordinate)}. The set of relation kinds is
   * closed so that every kind has a defined meaning during resolution.
   */
  sealed interface Relation permits Relation.Required, Relation.Optional, Relation.Incompatible, Relation.Embedded {

    /**
     * Returns the related catalog coordinate.
     *
     * @return related coordinate at any level
     */
    @NonNull
    Coordinate coordinate();

    /**
     * Content that must be installed alongside.
     *
     * @param coordinate required coordinate
     */
    record Required(@NonNull Coordinate coordinate) implements Relation {
    }

    /**
     * Content that enhances this artifact when present.
     *
     * @param coordinate optional coordinate
     */
    record Optional(@NonNull Coordinate coordinate) implements Relation {
    }

    /**
     * Content that may not be installed alongside.
     *
     * @param coordinate incompatible coordinate
     */
    record Incompatible(@NonNull Coordinate coordinate) implements Relation {
    }

    /**
     * Content already bundled inside this artifact.
     *
     * @param coordinate embedded coordinate
     */
    record Embedded(@NonNull Coordinate coordinate) implements Relation {
    }

  }

  /**
   * Every content family the loaded modules register, and the installs they
   * contribute, against the distributions targets are named in. What a
   * distribution supports is derived from the installs: a family is supported
   * where one of its installs applies.
   *
   * @param distributions the distributions targets are named in
   * @param families      every registered content family
   * @param installs      every contributed install, and the precedence
   *                      between them
   */
  record Registry(
      @NonNull Distribution.Registry distributions,
      @NonNull Set<Class<? extends Artifact>> families,
      @NonNull Contributions<Install.Signature> installs) {

    /**
     * Copies the families.
     */
    public Registry {
      families = Set.copyOf(families);
    }

    /**
     * Whether a distribution supports a family in an environment: whether an
     * install of the family applies to the distribution there, in some
     * version on some platform. Which install would be chosen does not matter
     * here, only that one applies.
     *
     * @param distribution distribution of a target
     * @param environment  environment of the target
     * @param family       family or concrete type of an artifact
     * @return whether the target can install it
     */
    public boolean supports(
        @NonNull Distribution distribution,
        @NonNull Environment environment,
        @NonNull Class<? extends Artifact> family) {
      Set<String> names = distributions.names(distribution);
      return installs.contributions().stream()
          .map(Contributions.Contributed::contributor)
          .filter(install -> install.conditions().regions().stream()
              .anyMatch(region -> region.appliesTo(names) && region.appliesTo(environment)))
          .anyMatch(install -> family.isAssignableFrom(install.subject())
              || install.subject().isAssignableFrom(family));
    }

    /**
     * Assesses an artifact against a target: unsupported when no install of
     * its family applies to the target's distribution there, supported when
     * the artifact also declares the target, and unknown otherwise. No
     * compatibility between runtime forks is inferred.
     *
     * @param artifact artifact to assess
     * @param target   target to assess it against
     * @return structural rejection, declared support, or unknown support
     */
    public Compatibility compatibility(@NonNull Artifact artifact, @NonNull Label target) {
      if (!supports(target.distribution(), target.environment(), artifact.getClass())) {
        return Compatibility.UNSUPPORTED;
      }
      return artifact.labels().stream().anyMatch(label -> label.match(target))
          ? Compatibility.SUPPORTED
          : Compatibility.UNKNOWN;
    }

    /**
     * Assesses an addon build against a target: unsupported when one of its
     * artifacts is, supported when the build advertises the target and every
     * artifact is supported there, and unknown otherwise.
     *
     * @param build  build to assess
     * @param target target to assess it against
     * @return structural rejection, declared support, or unknown support
     */
    public Compatibility compatibility(@NonNull Addon.Build build, @NonNull Label target) {
      Set<Compatibility> assessments = build.content().stream()
          .map(artifact -> compatibility(artifact, target))
          .collect(Collectors.toSet());
      if (assessments.contains(Compatibility.UNSUPPORTED)) {
        return Compatibility.UNSUPPORTED;
      }
      return build.labels().stream().anyMatch(label -> label.match(target))
          && assessments.stream().allMatch(result -> result == Compatibility.SUPPORTED)
              ? Compatibility.SUPPORTED
              : Compatibility.UNKNOWN;
    }
  }
}
