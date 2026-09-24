package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Intermediate;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Java loader mod content. Compatibility is declared explicitly per
 * distribution;
 * support for one server fork does not imply support for another.
 */
public sealed interface Mod extends Artifact {
  /**
   * An artifact using the shared named-file and ZIP capture artist.
   *
   * @param coordinates  exact provider file coordinates
   * @param artifactName non-blank display name
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
      @NonNull Set<Coordinate> conflicts) implements Mod {
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
      Artifact.validate(Mod.class, coordinates, artifactName, store, labels, depends, conflicts);
    }
  }

  /**
   * An artifact whose content images are described by a caller-supplied artist.
   *
   * @param coordinates  exact provider file coordinates
   * @param artifactName non-blank display name
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
      @NonNull Installer<Artifact.Context> installer) implements Mod {
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
      Artifact.validate(Mod.class, coordinates, artifactName, store, labels, depends, conflicts);
    }

    @Override
    public Intermediate<Set<Image<?>>> install(@NonNull Context context) throws IOException {
      return installer().install(context);
    }
  }
}
