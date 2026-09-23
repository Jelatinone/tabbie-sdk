package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Intermediate;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.api.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Self-contained pack content owned by one artifact, including every extracted
 * file. Embedded mods are not separate artifacts or external dependencies.
 * Java loaders default to ZIP extraction beneath their mods directory; explicit
 * layouts may choose another directory or keep the original archive intact.
 * Client and server variants are represented by separate labeled artifacts.
 */
public sealed interface Modpack extends Artifact {
  /**
   * An artifact using the shared named-file and ZIP capture artist.
   *
   * @param coordinates  exact provider file coordinates
   * @param artifactName nonblank display name
   * @param store        caller-owned source store
   * @param labels       nonempty supported targets
   * @param depends      external dependencies
   * @param conflicts    external conflicts
   */
  record Default(
    @NonNull Coordinate.File coordinates,
    @NonNull String artifactName,
    @NonNull Store store,
    @NonNull Set<Label> labels,
    @NonNull Set<Coordinate> depends,
    @NonNull Set<Coordinate> conflicts) implements Modpack {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relationships are
     *                                  invalid
     */
    public Default {
      labels = Set.copyOf(labels);
      depends = Set.copyOf(depends);
      conflicts = Set.copyOf(conflicts);
      Artifact.validate(Modpack.class, coordinates, artifactName, store, labels, depends, conflicts);
    }

    @Override
    public Intermediate<Set<Image<?>>> images(@NonNull Context context) throws IOException {
      return Artifact.DEFAULT_ARTIST.paint(this, context);
    }
  }

  /**
   * An artifact whose content images are described by a caller-supplied artist.
   *
   * @param coordinates  exact provider file coordinates
   * @param artifactName nonblank display name
   * @param store        caller-owned source store
   * @param labels       nonempty supported targets
   * @param depends      external dependencies
   * @param conflicts    external conflicts
   * @param artist       typed content description strategy
   */
  record Custom(
    @NonNull Coordinate.File coordinates,
    @NonNull String artifactName,
    @NonNull Store store,
    @NonNull Set<Label> labels,
    @NonNull Set<Coordinate> depends,
    @NonNull Set<Coordinate> conflicts,
    @NonNull Artist<? super Modpack> artist) implements Modpack {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relationships are
     *                                  invalid
     */
    public Custom {
      labels = Set.copyOf(labels);
      depends = Set.copyOf(depends);
      conflicts = Set.copyOf(conflicts);
      Artifact.validate(Modpack.class, coordinates, artifactName, store, labels, depends, conflicts);
    }

    @Override
    public Intermediate<Set<Image<?>>> images(@NonNull Context context) throws IOException {
      return artist().paint(this, context);
    }
  }
}
