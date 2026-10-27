package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import lombok.NonNull;

/**
 * Self-contained pack content owned by one artifact, including every extracted
 * file. Embedded mods are not separate artifacts or external dependencies. A
 * mod loader's layout accepts modpacks at the root of the Cat, where they
 * unpack; other distributions accept none.
 *
 * Client and server variants are published as separate target-specific builds,
 * because every artifact of a build installs on every target it advertises.
 * Manifest formats that download their contents, such as Modrinth's or
 * CurseForge's, need an importer rather than this artifact.
 */
public sealed interface Modpack extends Artifact {

  /**
   * An artifact unpacked over the directory accepting modpacks.
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
      Artifact.validate(Modpack.class, coordinates, artifactName, labels, relations);
    }

    /**
     * Unpacks the modpack over the directory of the collapsing plate's tree
     * that accepts modpacks: the root of a Cat on a mod loader. Other
     * distributions accept no modpack, so installing one there fails with
     * {@link cat.tabbie.sdk.platform.layout.Violation.Unplaced Unplaced}.
     *
     * @param context selected target
     * @return unevaluated capture producing validated immutable images
     * @throws IllegalArgumentException when the target is not explicitly
     *                                  supported
     */
    @Override
    public Intermediate<Set<Image<?>>> install(@NonNull Artifact.Context context) {
      require(context);
      return Intermediate.of(plate -> plate.root().find(getClass()).unique())
          .flatMap(directory -> context.archive().unpack(source(), directory, Address.root()));
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
      @NonNull Installer<Artifact.Context> installer) implements Modpack, Artifact.Custom {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relations are
     *                                  invalid
     */
    public Custom {
      labels = Set.copyOf(labels);
      relations = Set.copyOf(relations);
      Artifact.validate(Modpack.class, coordinates, artifactName, labels, relations);
    }
  }
}
