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
 * Self-contained pack content owned by one artifact, including every extracted
 * file. Embedded mods are not separate artifacts or external dependencies. A
 * mod loader's layout accepts modpacks at the root of the Cat, where they
 * unpack.
 *
 * Client and server variants are published as separate target-specific builds,
 * because every artifact of a build installs on every target it advertises.
 * Manifest formats that download their contents, such as Modrinth's or
 * CurseForge's, need an importer rather than this artifact.
 */
@Registered
@Distributions({"java:vanilla", "java:launcher", "bedrock:base"})
public sealed interface Modpack extends Artifact {

  /**
   * Unpacks the modpack over the directory of the Cat's model that accepts
   * modpacks, such as the root of a Cat on a mod loader, on vanilla Java, any
   * Java mod loader, and every Bedrock distribution.
   *
   * @param context the work, and where modpacks go in the Cat
   * @return validated immutable images
   * @throws IOException when the modpack cannot be captured or is refused
   * @throws Violation   when the model does not allow a file where the
   *                     modpack puts it, such as {@link Violation.Unplaced}
   *                     when no directory accepts modpacks
   */
  @Install
  default Set<Image<?>> installRoot(@NonNull Install.Context context) throws IOException {
    return context.place().unpack(source());
  }

  /**
   * A modpack as its provider describes it.
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
      @NonNull Set<Relation> relations) implements Modpack {
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
