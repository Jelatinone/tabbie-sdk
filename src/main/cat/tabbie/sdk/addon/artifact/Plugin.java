package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.meta.action.Install;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Plugin-server content. Compatibility is declared explicitly per
 * distribution; support for one server fork does not imply support for
 * another.
 */
@Registered
@Distributions({"java:manager", "bedrock:manager"})
public sealed interface Plugin extends Artifact {

  /**
   * Places the plugin as one file named after its source, in the directory
   * of the Cat's model accepting plugins, on every Java or Bedrock plugin
   * server.
   *
   * @param context the work, and where plugins go in the Cat
   * @return validated immutable images
   * @throws IOException when the plugin cannot be captured
   * @throws Violation   when the model does not allow the file, such as
   *                     {@link Violation.Unplaced} when no directory accepts
   *                     plugins
   */
  @Install
  default Set<Image<?>> installRoot(@NonNull Install.Context context) throws IOException {
    return context.place().file(source());
  }

  /**
   * A plugin as its provider describes it.
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
      @NonNull Set<Relation> relations) implements Plugin {
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
