package cat.tabbie.sdk.content;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.catalog.Addon;
import cat.tabbie.sdk.catalog.Coordinate;
import cat.tabbie.sdk.catalog.Identity;
import cat.tabbie.sdk.catalog.Product;
import cat.tabbie.sdk.catalog.Release;
import cat.tabbie.sdk.distribution.Distribution;
import cat.tabbie.sdk.meta.action.Closure;
import cat.tabbie.sdk.meta.action.Contributor;
import cat.tabbie.sdk.meta.attribute.Region;
import cat.tabbie.sdk.meta.registry.Contribution;
import cat.tabbie.sdk.meta.registry.Contributions;
import cat.tabbie.sdk.meta.registry.Registrable;
import cat.tabbie.sdk.model.Layout;
import cat.tabbie.sdk.model.World;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.revision.Image;
import cat.tabbie.sdk.target.Compatibility;
import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Label;
import cat.tabbie.sdk.target.Parity;
import cat.tabbie.sdk.target.Platform;
import lombok.NonNull;

/**
 * Provider-described content. How it is placed is contributed for its family
 * by {@link cat.tabbie.sdk.content.Install} methods, which capture bytes
 * from the artifact's source through the {@link Capture} of the work it is
 * installed in and describe changes without writing to the Cat's files; a
 * {@link Registry} chooses the install that applies to a target, and what a
 * distribution supports follows from them. Sources remain
 * caller-owned. File coordinates identify the exact provider-scoped payload;
 * relations name catalog coordinates at any level, matched by
 * {@link Coordinate#includes(Coordinate)}.
 */
@Registrable(Registrable.Kind.TYPES)
public interface Artifact extends Release.Payload {

  /**
   * Stable artifact identity derived from the file coordinates, so equal
   * coordinates always yield the same identity across processes.
   *
   * @return artifact identity
   */
  default Identity<Artifact> artifactId() {
    return Identity.create(coordinates().canonical());
  }

  /**
   * Human-readable canonical artifact name
   *
   * @return artifact name
   */
  @NonNull
  String artifactName();

  /**
   * Provider-owned description of this artifact's content, captured during
   * installation.
   *
   * @return content source
   */
  @NonNull
  Describe source();

  /**
   * Declares support for the entire artifact.
   *
   * @return immutable, nonempty supported targets
   */
  @NonNull
  Set<Label> labels();

  /**
   * Declares which multiplayer sides this artifact runs on and what it
   * requires of the other side.
   *
   * @return side parity, {@link Parity#UNKNOWN} without evidence
   */
  @NonNull
  Parity parity();

  /**
   * Catalog relationships with other content, at most one per coordinate.
   *
   * @return immutable relations
   */
  @NonNull
  Set<Relation> relations();

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
   * Checks copied constructor parameters before record fields are assigned.
   * Whether a declared target supports the artifact's family depends on the
   * installs core has loaded, so it is not checked here: core decides it
   * while resolving, through {@link Registry#compatibility(Artifact, Label)}.
   *
   * @param coordinates  exact file coordinates
   * @param artifactName display name
   * @param labels       declared targets
   * @param relations    catalog relationships
   *
   * @throws IllegalArgumentException when the name is blank, no target is
   *                                  declared, a relation includes this
   *                                  artifact, a coordinate has two relations,
   *                                  or an incompatibility includes another
   *                                  relation
   */
  static void validate(
      @NonNull Coordinate.File coordinates,
      @NonNull String artifactName,
      @NonNull Set<Label> labels,
      @NonNull Set<Relation> relations) {
    if (artifactName.isBlank() || labels.isEmpty()) {
      throw new IllegalArgumentException("An artifact needs a non-blank name and supported labels.");
    }
    if (relations.stream().anyMatch(relation -> relation.coordinate().includes(coordinates))) {
      throw new IllegalArgumentException("Relations must not reference this artifact.");
    }
    if (relations.stream().map(Relation::coordinate).distinct().count() != relations.size()) {
      throw new IllegalArgumentException("A coordinate may have only one relation.");
    }
    if (relations.stream()
        .filter(relation -> relation instanceof Relation.Incompatible)
        .anyMatch(conflict -> relations.stream()
            .filter(other -> !(other instanceof Relation.Incompatible))
            .anyMatch(other -> conflict.coordinate().includes(other.coordinate())))) {
      throw new IllegalArgumentException("An incompatibility must not include another relation.");
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
   * The work content is placed in, and the Worlds its content applies to.
   * Where content goes is not the context's to say: an artifact finds the
   * directory of the model accepting its type, or, when its content belongs to
   * a World, the directory of each of the Worlds accepting it.
   */
  interface Context extends Work {

    /**
     * Returns the Worlds that content belonging to a World is installed into,
     * such as those whose Collar uses it, each held by the model. A
     * client's selected saves and a server's Worlds are alike a set of Worlds.
     *
     * @return immutable Worlds, empty when none applies
     */
    @NonNull
    Set<World> worlds();

    /**
     * Content's context in some work, answering the work's parts from it.
     *
     * @param work   the work content is placed in
     * @param worlds Worlds of the work's model that content belonging to a
     *               World applies to
     */
    record Default(@NonNull Work work, @NonNull Set<World> worlds) implements Context {

      /**
       * Copies the Worlds.
       */
      public Default {
        worlds = Set.copyOf(worlds);
      }

      /**
       * Creates a context that applies to no World.
       *
       * @param work the work content is placed in
       */
      public Default(@NonNull Work work) {
        this(work, Set.of());
      }

      @Override
      public Label label() {
        return work.label();
      }

      @Override
      public Platform platform() {
        return work.platform();
      }

      @Override
      public Layout.Directory model() {
        return work.model();
      }

      @Override
      public Capture capture() {
        return work.capture();
      }
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
      return applying(distribution, environment, _ -> true)
          .anyMatch(install -> family.isAssignableFrom(install.subject()) || install.subject().isAssignableFrom(family));
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

    /**
     * Installs an artifact on a target with no preference, as
     * {@link #install(Artifact, Context, Set)} does.
     *
     * @param artifact artifact to install
     * @param context  the work and Worlds the install draws on
     * @return validated immutable images
     * @throws IOException              as the install throws
     * @throws IllegalArgumentException when no install of the artifact applies
     *                                  to the target
     * @throws IllegalStateException    when installs clash at the target
     */
    public Set<Image<?>> install(@NonNull Artifact artifact, @NonNull Context context) throws IOException {
      return install(artifact, context, Set.of());
    }

    /**
     * Installs an artifact on a target, through the install that wins by
     * precedence, or by the user's preference among those precedence cannot
     * decide between, acting where its closure says, and checks the images it
     * describes.
     *
     * @param artifact  artifact to install
     * @param context   the work and Worlds the install draws on
     * @param preferred {@link Contributions.Contributed#identity() identities}
     *                  of the installs the user prefers
     * @return validated immutable images
     * @throws IOException              as the install throws
     * @throws IllegalArgumentException when no install of the artifact applies
     *                                  to the target, which core rules out
     *                                  while resolving
     * @throws IllegalStateException    when installs clash at the target and
     *                                  the user prefers none of them, which
     *                                  core asks about first
     */
    public Set<Image<?>> install(
        @NonNull Artifact artifact,
        @NonNull Context context,
        @NonNull Set<String> preferred) throws IOException {
      Label label = context.label();
      Contributor<Class<?>, Install.Signature> install = switch (installs.choose(artifact.getClass(),
          distributions.target(label, context.platform()), preferred)) {
        case Contribution.Concur<Install.Signature>(var chosen) when chosen.isEmpty() ->
            throw new IllegalArgumentException(String.format("No install of %s applies to %s.",
                artifact.getClass().getName(), label.canonical()));
        case Contribution.Concur<Install.Signature>(var chosen) -> chosen.getFirst().contributor();
        case Contribution.Clash<Install.Signature> clash -> throw new IllegalStateException(clash.message());
      };
      Set<Image<?>> images = install.call().install(artifact, Install.Context.of(artifact, context,
          install.closure()));
      Image.validate(images);
      return Set.copyOf(images);
    }

    /**
     * Checks a release's layout places what every install applying to the
     * release places, where the install's closure says it acts: an install
     * acting at the root needs its subject accepted in exactly one directory,
     * and one acting in namespaces needs Worlds accepted in exactly one, where
     * core mounts each World's namespace. An install applies to the release
     * when one of its regions applies to its distribution and environment,
     * and to the version of one of its labels, on some platform. The layout
     * may accept more, for content installed anyway.
     *
     * @param build release to check
     * @throws IllegalArgumentException when an applying install's subject, or
     *                                  Worlds, have no accepting directory or
     *                                  several
     */
    public void check(@NonNull Product.Build build) {
      List<Contributor<Class<?>, Install.Signature>> applying = applying(build.distribution(), build.environment(),
          region -> build.labels().stream().anyMatch(label -> region.versions().appliesTo(label.version())))
          .toList();
      applying.stream()
          .filter(install -> install.closure() == Closure.ROOT)
          .filter(install -> build.layout().find(install.subject()).directories().size() != 1)
          .findFirst()
          .ifPresent(install -> {
            throw new IllegalArgumentException(String.format("%s installs %s at its root, so its layout accepts it "
                + "in exactly one directory.", build.coordinates().canonical(), install.subject().getName()));
          });
      applying.stream()
          .filter(install -> install.closure() == Closure.NAMESPACES)
          .filter(install -> build.layout().find(World.class).directories().size() != 1)
          .findFirst()
          .ifPresent(install -> {
            throw new IllegalArgumentException(String.format("%s installs %s in Worlds, so its layout accepts "
                + "Worlds in exactly one directory.", build.coordinates().canonical(), install.subject().getName()));
          });
    }

    /**
     * Installs with a region applying to a distribution in an environment,
     * on any platform, at versions the region's own test accepts.
     */
    private Stream<Contributor<Class<?>, Install.Signature>> applying(
        Distribution distribution,
        Environment environment,
        Predicate<Region> versions) {
      Set<String> names = distributions.names(distribution);
      return installs.contributions().stream()
          .map(Contributions.Contributed::contributor)
          .filter(install -> install.conditions().regions().stream()
              .anyMatch(region -> region.appliesTo(names) && region.appliesTo(environment) && versions.test(region)));
    }
  }
}
