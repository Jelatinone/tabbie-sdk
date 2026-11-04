package cat.tabbie.sdk.addon.artifact;

import static cat.tabbie.sdk.meta.action.Closure.NAMESPACES;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.meta.action.Install;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.minecraft.World;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Java world data content, which belongs to a World and installs into the
 * Worlds its context names. Constructors do not inspect pack metadata or
 * discover world directories.
 */
@Registered
@Distributions("java:base")
public sealed interface Datapack extends Artifact, World.Content {

  /**
   * Places the datapack as one file named after its source in each World of
   * the context, in the World's directory accepting datapacks, on every Java
   * distribution. The game loads a zipped datapack as it is, and unpacking
   * one wrapped in a folder of its own would bury its {@code pack.mcmeta} too
   * deep to load.
   *
   * @param context the work, and where datapacks go in every World
   * @return validated immutable images
   * @throws IOException when the datapack cannot be captured
   * @throws Violation   when the model does not allow the file in a World
   */
  @Install(NAMESPACES)
  default Set<Image<?>> installNamespaces(@NonNull Install.Context context) throws IOException {
    return context.place().file(source());
  }

  /**
   * A datapack as its provider describes it.
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
      Artifact.validate(coordinates, artifactName, labels, relations);
    }
  }
}
