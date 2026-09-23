package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Archive;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Intermediate;
import cat.tabbie.sdk.album.Reference.Pending;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.api.Release;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Provider-described content and its logical placement. Artists capture bytes
 * and describe changes without writing to the target installation filesystem.
 * Stores remain caller-owned. Artifact identities refer to exact
 * provider-scoped payload selections; dependencies/conflicts name those same
 * identities.
 */
public interface Artifact extends Release.Payload {

  /**
   * Shared source-to-retention capture strategy.
   */
  Artist<Artifact> DEFAULT_ARTIST = new Artist.Default();

  /**
   * Stable artifact identity
   *
   * @return artifact identity
   */
  @NonNull
  Identity<Artifact> artifactId();

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
   * Artifact identities that must be installed alongside this artifact
   *
   * @return depending artifact identities
   */
  @NonNull
  Set<Identity<Artifact>> depends();

  /**
   * Artifact identities that may not be installed alongside this artifact
   *
   * @return conflicting artifact identities
   */
  @NonNull
  Set<Identity<Artifact>> conflicts();

  /**
   * Payload installation images relative to this type's installation root.
   *
   * @param context target mounts and retention store
   * @return installation images
   * @throws IOException when acquisition, capture, or layout resolution fails
   */
  @NonNull
  Intermediate<Set<Image<?>>> images(@NonNull Context context) throws IOException;

  /**
   * Checks copied constructor parameters before record fields are assigned.
   *
   * @param artifactFamily artifact family token
   * @param artifactId     exact artifact identity
   * @param artifactName   display name
   * @param store          source store
   * @param labels         declared targets
   * @param depends        required identities
   * @param conflicts      incompatible identities
   *
   * @throws IllegalArgumentException when declarations contradict structural
   *                                  facts
   */
  static void validate(
      @NonNull Class<? extends Artifact> artifactFamily,
      @NonNull Identity<Artifact> artifactId,
      @NonNull String artifactName,

      @NonNull Store store,

      @NonNull Set<Label> labels,

      @NonNull Set<Identity<Artifact>> depends,
      @NonNull Set<Identity<Artifact>> conflicts) {
    if (artifactName.isBlank() || labels.isEmpty()) {
      throw new IllegalArgumentException("An artifact needs a nonblank name and supported labels.");
    }
    if (labels.stream().anyMatch(label -> !label.distribution().supports(artifactFamily))) {
      throw new IllegalArgumentException("A declared distribution does not support this artifact family.");
    }
    if (depends.contains(artifactId) || conflicts.contains(artifactId)
        || depends.stream().anyMatch(conflicts::contains)) {
      throw new IllegalArgumentException("External relationships must not reference self or contradict each other.");
    }
  }

  /**
   * Requires an explicitly supported target before invoking an artist.
   *
   * @param artifact source artifact
   * @param context  selected target
   */
  static void require(@NonNull Artifact artifact, @NonNull Context context) {
    if (context.label().compatibility(artifact) != Compatibility.SUPPORTED) {
      throw new IllegalArgumentException("Image generation requires explicit target support.");
    }
  }

  /**
   * Describes content without applying it.
   *
   * @param <Canvas> accepted artifact family
   */
  @FunctionalInterface
  interface Artist<Canvas extends Artifact> {

    /**
     * Captures and describes the supplied content.
     *
     * @param artifact source artifact
     * @param context  target context
     * @return described images
     * @throws IOException when capture or layout resolution fails
     */
    Intermediate<Set<Image<?>>> paint(@NonNull Canvas artifact, @NonNull Context context) throws IOException;

    /**
     * Shared source-to-retention capture strategy.
     */
    record Default() implements Artist<Artifact> {

      @Override
      public Intermediate<Set<Image<?>>> paint(@NonNull Artifact artifact, @NonNull Context context)
          throws IOException {
        require(artifact, context);
        Layout layout = context.layout(artifact);
        Path destination = context.resolve(layout);

        Pending reference = context.store().of();
        if (layout.unpack()) {
          return Archive.DEFAULT.unpack(context.store(), destination);
        } else {
          try (InputStream in = context.store().open(reference)) {
            return artifact.store().capture(in)
                .map((redference) -> Image.create(
                    destination.resolve(reference.fileName()), reference))
                .map(Set::of);
          }

        }
      }

    }
  }

  /**
   * Logical target mounts and caller-owned retention, independent of physical
   * paths.
   */
  interface Context {

    /**
     * Returns explicitly selected target.
     *
     * @return explicitly selected target
     */
    @NonNull
    Label label();

    /**
     * Returns caller-owned retention store.
     *
     * @return caller-owned retention store
     */
    @NonNull
    <S extends Store & Store.Extract> S store();

    /**
     * Returns mount relative to the Den, empty for its working directory.
     *
     * @return mount relative to the Den, empty for its working directory
     */
    @NonNull
    Path contextRoot();

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
      require(artifact, this);
      Layout override = layouts().get(artifact.artifactId());
      return override != null ? override : label().distribution().layout(artifact, this);
    }

    /**
     * Immutable context with explicit root and world mounts.
     *
     * @param label       selected target
     * @param store       retention store
     * @param contextRoot Den-relative context mount
     * @param worldRoot   selected world relative to the context mount
     */
    record Default<S extends Store & Store.Extract>(@NonNull Label label, @NonNull S store, @NonNull Path contextRoot,
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
