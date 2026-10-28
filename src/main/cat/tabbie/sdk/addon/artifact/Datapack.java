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
 * Java world data content, which belongs to a World and so cannot be
 * installed until the World class exists. Constructors do not inspect pack
 * metadata or discover world directories.
 */
public sealed interface Datapack extends Artifact {

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
      @NonNull Set<Relation> relations) implements Datapack {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relations are
     *                                  invalid
     */
    public Default {
      labels = Set.copyOf(labels);
      relations = Set.copyOf(relations);
      Artifact.validate(Datapack.class, coordinates, artifactName, labels, relations);
    }

    /**
     * Refuses to install until a World can be found, since datapacks belong
     * to a World.
     *
     * @param context selected target
     * @return never
     * @throws UnsupportedOperationException always, until the World class
     *                                       exists
     */
    @Override
    public Set<Image<?>> install(@NonNull Artifact.Context context) {
      // TODO: Installing a datapack needs the World class. A datapack unpacks
      // into <world>/datapacks/<id>/, but a layout cannot name a Cat's Worlds,
      // so no directory accepts datapacks, and finding one by where they are
      // allowed is not the way. Before, Installer.Context#world() named the
      // selected World's directory, Distribution.layout resolved
      // datapacks/<id>/ beneath it, and Artifact.Context#layout required the
      // result to lie inside that World.
      throw new UnsupportedOperationException("Installing a datapack needs the World class, which is not built yet.");
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
      @NonNull Installer<Artifact.Context> installer) implements Datapack, Artifact.Custom {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relations are
     *                                  invalid
     */
    public Custom {
      labels = Set.copyOf(labels);
      relations = Set.copyOf(relations);
      Artifact.validate(Datapack.class, coordinates, artifactName, labels, relations);
    }
  }
}
