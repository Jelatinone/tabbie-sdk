package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.*;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Provider-described content and its logical placement. Installers capture
 * bytes and describe changes without writing to the target installation
 * filesystem. Stores remain caller-owned. File coordinates identify the exact
 * provider-scoped payload; dependencies and conflicts name catalog coordinates
 * at any level, matched by {@link Coordinate#includes(Coordinate)}.
 */
public sealed interface Artifact extends Release.Payload<Artifact.Context>
    permits Mod, Plugin, Datapack, Behaviourpack, Modpack, Resourcepack {

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
   * Delegated storage receiving capture/open calls
   *
   * @return delegated storage
   */
  Store store();

  /**
   * Declares support for the entire artifact.
   *
   * @return immutable, nonempty supported targets
   */
  @NonNull
  Set<Label> labels();

  /**
   * Catalog content that must be installed alongside this artifact. A project
   * coordinate is satisfied by any file of that project, a build coordinate by
   * any file of that release, and a file coordinate only by that exact file.
   *
   * @return required coordinates
   */
  @NonNull
  Set<Coordinate> depends();

  /**
   * Catalog content that may not be installed alongside this artifact, matched
   * the same way as {@link #depends()}.
   *
   * @return conflicting coordinates
   */
  @NonNull
  Set<Coordinate> conflicts();

  @Override
  default Intermediate<Set<Image<?>>> install(@NonNull Context context) throws IOException {
    this.require(context);
    Layout layout = context.layout(this);
    Path destination = context.resolve(layout);

    Store store = context.store();
    Intermediate<Reference.Captured> retained = store.capture(store);
    if (layout.unpack()) {
      return retained
          .flatMap(archive -> Archive.DEFAULT.unpack(store, destination))
          .map((images) -> Image.fence(images, destination));
    }
    return retained
        .map(captured -> List.of(Image.create(destination.resolve(captured.fileName()), captured)))
        .map((images) -> Image.fence(images, destination));
  }

  /**
   * Checks copied constructor parameters before record fields are assigned.
   *
   * @param artifactFamily artifact family token
   * @param coordinates    exact file coordinates
   * @param artifactName   display name
   * @param store          source store
   * @param labels         declared targets
   * @param depends        required coordinates
   * @param conflicts      incompatible coordinates
   *
   * @throws IllegalArgumentException when declarations contradict structural
   *                                  facts, a relationship includes this
   *                                  artifact, or a conflict includes a
   *                                  dependency
   */
  static void validate(
      @NonNull Class<? extends Artifact> artifactFamily,
      @NonNull Coordinate.File coordinates,
      @NonNull String artifactName,

      @NonNull Store store,

      @NonNull Set<Label> labels,

      @NonNull Set<Coordinate> depends,
      @NonNull Set<Coordinate> conflicts) {
    if (artifactName.isBlank() || labels.isEmpty()) {
      throw new IllegalArgumentException("An artifact needs a non-blank name and supported labels.");
    }
    if (labels.stream().anyMatch(label -> !label.distribution().supports(artifactFamily))) {
      throw new IllegalArgumentException("A declared distribution does not support this artifact family.");
    }
    if (Stream.concat(depends.stream(), conflicts.stream()).anyMatch(relation -> relation.includes(coordinates))
        || conflicts.stream().anyMatch(conflict -> depends.stream().anyMatch(conflict::includes))) {
      throw new IllegalArgumentException("External relationships must not reference self or contradict each other.");
    }
  }

  /**
   * Requires an explicitly supported target before invoking an installer.
   *
   * @param artifact source artifact
   * @param context  selected target
   */
  default void require(@NonNull Context context) {
    if (context.label().compatibility(this) != Compatibility.SUPPORTED) {
      throw new IllegalArgumentException("Image generation requires explicit target support.");
    }
  }

  /**
   * Logical target mounts and caller-owned retention, independent of physical
   * paths.
   */
  public interface Context extends Installer.Context {

    /**
     * Returns explicitly selected target.
     *
     * @return explicitly selected target
     */
    @NonNull
    Label label();

    /**
     * Returns selected world mount relative to this context's root, if selected.
     *
     * @return selected world mount relative to this context's root, if selected
     */
    @NonNull
    Path worldRoot();

    /**
     * Returns per-artifact layout overrides.
     *
     * @return per-artifact layout overrides
     */
    default Map<Identity<Artifact>, Layout> layouts() {
      return Map.of();
    }

    /**
     * Binds a scoped layout to a normalized Den-relative destination.
     *
     * @param layout scoped destination
     * @return Den-relative directory
     * @throws IOException when a world layout has no selected world mount
     */
    default Path resolve(@NonNull Layout layout) throws IOException {
      Path mount = Image.relative(contextRoot());
      if (layout instanceof Layout.World) {
        mount = mount
            .resolve(Image.relative(worldRoot()));
      }
      return Image.relative(mount.resolve(layout.relativePath()));
    }

    /**
     * Selects an override or the distribution's default layout.
     *
     * @param artifact selected artifact
     * @return scoped logical layout
     * @throws IOException when no default or override exists
     */
    default Layout layout(@NonNull Artifact artifact) throws IOException {
      artifact.require(this);
      Layout override = layouts().get(artifact.artifactId());
      return override != null
          ? override
          : label().distribution().layout(artifact, this);
    }

    /**
     * Immutable context with explicit root and world mounts.
     *
     * @param label       selected target
     * @param store       retention store
     * @param contextRoot Den-relative context mount
     * @param worldRoot   selected world relative to the context mount
     */
    record Default<S extends Store & Extract>(@NonNull Label label, @NonNull S store, @NonNull Path contextRoot,
        @NonNull Path worldRoot)
        implements Context {
      /**
       * Validates and normalizes both logical mounts without filesystem access.
       */
      public Default {
        contextRoot = Image.relative(contextRoot);
        worldRoot = Image.relative(worldRoot);
      }

    }
  }

  /**
   * Scoped directory; an unpacking layout names the exact extraction root.
   */
  sealed interface Layout
      permits Layout.Root, Layout.World {

    /**
     * Returns whether to capture ZIP entries instead of the original file.
     *
     * @return whether to capture ZIP entries instead of the original file
     */
    boolean unpack();

    /**
     * Returns normalized directory relative to the corresponding mount.
     *
     * @return normalized directory relative to the corresponding mount
     */
    @NonNull
    Path relativePath();

    /**
     * Directory relative to the assembled Den/context root.
     *
     * @param unpack       whether to unpack the payload
     * @param relativePath destination directory
     */
    record Root(boolean unpack, Path relativePath) implements Layout {

      /**
       * Validates and normalizes the mount-relative destination.
       */
      public Root {
        relativePath = Image.relative(relativePath);
      }
    }

    /**
     * Directory relative to the selected world's mount.
     *
     * @param unpack       whether to unpack the payload
     * @param relativePath destination directory
     */
    record World(boolean unpack, Path relativePath) implements Layout {

      /**
       * Validates and normalizes the world-relative destination.
       */
      public World {
        relativePath = Image.relative(relativePath);
      }
    }
  }
}
