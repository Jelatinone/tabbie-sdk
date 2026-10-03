package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * Provider-described content and its logical placement. Installers capture
 * bytes from the artifact's source into the context's retention backend and
 * describe changes without writing to the target installation filesystem.
 * Sources and retention backends remain caller-owned. File coordinates
 * identify the exact provider-scoped payload; relations name catalog
 * coordinates at any level, matched by {@link Coordinate#includes(Coordinate)}.
 */
public sealed interface Artifact extends Release.Payload<Artifact.Context>
    permits Mod, Plugin, Datapack, Behaviourpack, Modpack, Resourcepack, Artifact.Custom {

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
    return coordinates(Relation.Kind.REQUIRED);
  }

  /**
   * Catalog content that may not be installed alongside this artifact, matched
   * the same way as {@link #depends()}.
   *
   * @return conflicting coordinates
   */
  default Set<Coordinate> conflicts() {
    return coordinates(Relation.Kind.INCOMPATIBLE);
  }

  /**
   * Selects the coordinates of one kind of relation.
   *
   * @param kind relation kind
   * @return immutable coordinates of that kind
   */
  default Set<Coordinate> coordinates(@NonNull Relation.Kind kind) {
    return relations().stream()
        .filter(relation -> relation.kind() == kind)
        .map(Relation::coordinate)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Captures the source into the context's retention backend and places it at
   * the selected layout: as one named file, or unpacked beneath the layout
   * directory.
   *
   * @param context selected target
   * @return validated immutable images
   * @throws IOException when no layout can be determined
   */
  @Override
  default Intermediate<Set<Image<?>>> install(@NonNull Context context) throws IOException {
    Layout layout = context.layout(this);
    Extract repository = context.repository();
    return repository.capture(source())
        .flatMap(captured -> layout.unpack()
            ? Archive.DEFAULT.unpack(repository.retained(captured), repository, layout.relative())
            : Intermediate.of(Set.<Image<?>>of(
                Image.create(layout.relative().resolve(captured.fileName()), captured))))
        .map(Artifact::checked);
  }

  /**
   * Checks copied constructor parameters before record fields are assigned.
   *
   * @param artifactFamily artifact family token
   * @param coordinates    exact file coordinates
   * @param artifactName   display name
   * @param labels         declared targets
   * @param relations      catalog relationships
   *
   * @throws IllegalArgumentException when declarations contradict structural
   *                                  facts, a relation includes this artifact,
   *                                  a coordinate has two relations, or an
   *                                  incompatibility includes another relation
   */
  static void validate(
      @NonNull Class<? extends Artifact> artifactFamily,
      @NonNull Coordinate.File coordinates,
      @NonNull String artifactName,
      @NonNull Set<Label> labels,
      @NonNull Set<Relation> relations) {
    if (artifactName.isBlank() || labels.isEmpty()) {
      throw new IllegalArgumentException("An artifact needs a non-blank name and supported labels.");
    }
    if (labels.stream().anyMatch(label -> !label.distribution().supports(artifactFamily))) {
      throw new IllegalArgumentException("A declared distribution does not support this artifact family.");
    }
    if (relations.stream().anyMatch(relation -> relation.coordinate().includes(coordinates))) {
      throw new IllegalArgumentException("Relations must not reference this artifact.");
    }
    if (relations.stream().map(Relation::coordinate).distinct().count() != relations.size()) {
      throw new IllegalArgumentException("A coordinate may have only one relation.");
    }
    if (relations.stream()
        .filter(relation -> relation.kind() == Relation.Kind.INCOMPATIBLE)
        .anyMatch(conflict -> relations.stream()
            .filter(other -> other.kind() != Relation.Kind.INCOMPATIBLE)
            .anyMatch(other -> conflict.coordinate().includes(other.coordinate())))) {
      throw new IllegalArgumentException("An incompatibility must not include another relation.");
    }
  }

  /**
   * Requires an explicitly supported target before invoking an installer.
   *
   * @param context selected target
   * @throws IllegalArgumentException when the target is not explicitly
   *                                  supported
   */
  default void require(@NonNull Context context) {
    if (context.label().compatibility(this) != Compatibility.SUPPORTED) {
      throw new IllegalArgumentException("Image generation requires explicit target support.");
    }
  }

  /**
   * Checks installer output and returns an immutable copy.
   *
   * @param images generated images
   * @return validated immutable images
   */
  private static Set<Image<?>> checked(Set<Image<?>> images) {
    Image.validate(images);
    return Set.copyOf(images);
  }

  /**
   * An artifact whose images are described by a caller-supplied installer. The
   * same target support and image checks as the shared installer apply.
   */
  sealed interface Custom extends Artifact
      permits Mod.Custom, Plugin.Custom, Datapack.Custom, Behaviourpack.Custom, Modpack.Custom,
      Resourcepack.Java.Custom, Resourcepack.Bedrock.Custom {

    /**
     * Returns the caller-supplied installer.
     *
     * @return typed content description strategy
     */
    @NonNull
    Installer<Context> installer();

    @Override
    default Intermediate<Set<Image<?>>> install(@NonNull Context context) throws IOException {
      require(context);
      return installer().install(context).map(Artifact::checked);
    }
  }

  /**
   * A relationship with other catalog content.
   *
   * @param coordinate related catalog coordinate at any level
   * @param kind       relationship kind
   */
  record Relation(@NonNull Coordinate coordinate, @NonNull Kind kind) {

    /**
     * Content that must be installed alongside.
     *
     * @param coordinate required coordinate
     * @return required relation
     */
    public static Relation required(@NonNull Coordinate coordinate) {
      return new Relation(coordinate, Kind.REQUIRED);
    }

    /**
     * Content that enhances this artifact when present.
     *
     * @param coordinate optional coordinate
     * @return optional relation
     */
    public static Relation optional(@NonNull Coordinate coordinate) {
      return new Relation(coordinate, Kind.OPTIONAL);
    }

    /**
     * Content that may not be installed alongside.
     *
     * @param coordinate incompatible coordinate
     * @return incompatible relation
     */
    public static Relation incompatible(@NonNull Coordinate coordinate) {
      return new Relation(coordinate, Kind.INCOMPATIBLE);
    }

    /**
     * Content already bundled inside this artifact.
     *
     * @param coordinate embedded coordinate
     * @return embedded relation
     */
    public static Relation embedded(@NonNull Coordinate coordinate) {
      return new Relation(coordinate, Kind.EMBEDDED);
    }

    /**
     * Relationship kinds.
     */
    public enum Kind {

      /**
       * Must be installed alongside.
       */
      REQUIRED,

      /**
       * Enhances this artifact when present; never installed implicitly.
       */
      OPTIONAL,

      /**
       * May not be installed alongside.
       */
      INCOMPATIBLE,

      /**
       * Bundled inside this artifact; it satisfies requirements for that
       * content and is not installed separately.
       */
      EMBEDDED
    }
  }

  /**
   * Selected target, logical mounts, retention, and per-artifact layout
   * overrides, independent of physical paths.
   */
  public interface Context extends Installer.Context {

    /**
     * Returns the context mount as a context-scoped anchor for installer-built
     * paths.
     *
     * @return context mount itself
     */
    @NonNull
    default Relative.Root root() {
      return Relative.root();
    }

    /**
     * Returns the world mount as a world-scoped anchor for installer-built
     * paths. Resolving paths beneath it requires a bound {@link #worldRoot()}.
     *
     * @return world mount itself
     */
    @NonNull
    default Relative.World world() {
      return Relative.world();
    }

    /**
     * Returns per-artifact layout overrides.
     *
     * @return per-artifact layout overrides
     */
    default Map<Identity<Artifact>, Layout> layouts() {
      return Map.of();
    }

    /**
     * Selects an override or the distribution's default layout.
     *
     * @param artifact selected artifact
     * @return scoped logical layout
     * @throws IOException              when no default or override exists
     * @throws IllegalArgumentException when the target is not explicitly
     *                                  supported, or the layout is world-scoped
     *                                  and no world is bound
     */
    default Layout layout(@NonNull Artifact artifact) throws IOException {
      artifact.require(this);
      Layout override = layouts().get(artifact.artifactId());
      Layout layout = override != null
          ? override
          : label().distribution().layout(artifact, this);
      if (layout.relative() instanceof Relative.World && worldRoot().isEmpty()) {
        throw new IllegalArgumentException("World-scoped content requires a bound world.");
      }
      return layout;
    }

    /**
     * Immutable context with explicit mounts and layout overrides.
     *
     * @param label       selected target
     * @param platform    target platform
     * @param repository  caller-owned retention backend
     * @param contextRoot context mount relative to the installation
     * @param worldRoot   selected world relative to the context mount, if bound
     * @param layouts     per-artifact layout overrides
     */
    record Default(
        @NonNull Label label,
        @NonNull Platform platform,
        @NonNull Extract repository,
        @NonNull Relative.Root contextRoot,
        @NonNull Optional<Relative.Root> worldRoot,
        @NonNull Map<Identity<Artifact>, Layout> layouts) implements Context {

      /**
       * Copies the layout overrides.
       */
      public Default {
        layouts = Map.copyOf(layouts);
      }

      /**
       * Creates a context without a bound world or overrides.
       *
       * @param label       selected target
       * @param platform    target platform
       * @param repository  caller-owned retention backend
       * @param contextRoot context mount relative to the installation
       */
      public Default(@NonNull Label label, @NonNull Platform platform, @NonNull Extract repository,
          @NonNull Relative.Root contextRoot) {
        this(label, platform, repository, contextRoot, Optional.empty(), Map.of());
      }

      /**
       * Creates a context with a bound world and no overrides.
       *
       * @param label       selected target
       * @param platform    target platform
       * @param repository  caller-owned retention backend
       * @param contextRoot context mount relative to the installation
       * @param worldRoot   selected world relative to the context mount
       */
      public Default(@NonNull Label label, @NonNull Platform platform, @NonNull Extract repository,
          @NonNull Relative.Root contextRoot, @NonNull Relative.Root worldRoot) {
        this(label, platform, repository, contextRoot, Optional.of(worldRoot), Map.of());
      }
    }
  }

  /**
   * Scoped placement. A named-file layout names the directory receiving the
   * file; an unpacking layout names the exact extraction root.
   *
   * @param unpack   whether the content is a ZIP archive to extract
   * @param relative scoped destination directory
   */
  record Layout(boolean unpack, @NonNull Relative relative) {
  }
}
