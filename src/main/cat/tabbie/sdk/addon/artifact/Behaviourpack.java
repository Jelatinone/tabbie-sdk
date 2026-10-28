package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Describe;
import lombok.NonNull;

/**
 * Native Bedrock behaviour content. Alternative Bedrock plugin servers do not
 * support this artifact family. Placing the files does not activate the pack:
 * the world's {@code world_behavior_packs.json} must also list its manifest
 * UUID and version, which this SDK does not yet describe, so Bedrock packs are
 * not yet installable end to end.
 */
public sealed interface Behaviourpack extends Artifact {

  /**
   * An artifact that cannot be installed until the World class exists.
   *
   * @param coordinates  exact provider file coordinates
   * @param artifactName non-blank display name
   * @param source       provider-owned content description
   * @param labels       nonempty supported targets
   * @param parity       multiplayer side parity
   * @param relations    catalog relationships, one per coordinate
   */
  record Default(
      @NonNull Coordinate.File coordinates,
      @NonNull String artifactName,
      @NonNull Describe source,
      @NonNull Set<Label> labels,
      @NonNull Parity parity,
      @NonNull Set<Relation> relations) implements Behaviourpack {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relations are
     *                                  invalid
     */
    public Default {
      labels = Set.copyOf(labels);
      relations = Set.copyOf(relations);
      Artifact.validate(Behaviourpack.class, coordinates, artifactName, labels, relations);
    }

    /**
     * Refuses to install until a World can be found, since behaviour packs
     * belong to a World.
     *
     * @param context selected target and the plate it is installed on
     * @return never
     * @throws UnsupportedOperationException always, until the World class
     *                                       exists
     */
    @Override
    public Set<Image<?>> install(@NonNull Artifact.Context context) {
      // TODO: Installing a behaviour pack needs the World class. A behaviour
      // pack unpacks into <world>/behavior_packs/<id>/, but a layout cannot name
      // a Cat's Worlds, so no directory accepts behaviour packs, and finding one
      // by where they are allowed is not the way. Before,
      // Installer.Context#world() named the selected World's directory,
      // Distribution.layout resolved behavior_packs/<id>/ beneath it, and
      // Artifact.Context#layout required the result to lie inside that World.
      throw new UnsupportedOperationException(
          "Installing a behaviour pack needs the World class, which is not built yet.");
    }
  }

  /**
   * An artifact whose images are described by a caller-supplied installer.
   *
   * @param coordinates  exact provider file coordinates
   * @param artifactName non-blank display name
   * @param source       provider-owned content description
   * @param labels       nonempty supported targets
   * @param parity       multiplayer side parity
   * @param relations    catalog relationships, one per coordinate
   * @param installer    typed content description strategy
   */
  record Custom(
      @NonNull Coordinate.File coordinates,
      @NonNull String artifactName,
      @NonNull Describe source,
      @NonNull Set<Label> labels,
      @NonNull Parity parity,
      @NonNull Set<Relation> relations,
      @NonNull Installer<Artifact.Context> installer) implements Behaviourpack, Artifact.Custom {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relations are
     *                                  invalid
     */
    public Custom {
      labels = Set.copyOf(labels);
      relations = Set.copyOf(relations);
      Artifact.validate(Behaviourpack.class, coordinates, artifactName, labels, relations);
    }
  }
}
