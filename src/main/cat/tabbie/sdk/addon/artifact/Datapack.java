package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
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
public sealed interface Datapack extends Artifact, World.Content {

  /**
   * An artifact unpacked into each World of its context.
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
     * Unpacks the datapack into each World of the context, into a directory
     * of its own, named by its artifact identity, beneath the World's
     * directory accepting datapacks, so two datapacks never collide.
     *
     * @param context selected target, the plate it is installed on, and the
     *                Worlds it applies to
     * @return validated immutable images
     * @throws IOException        when the datapack cannot be captured or is
     *                            refused
     * @throws Violation.Unplaced when the context names no World, or a World
     *                            has no directory accepting datapacks
     */
    @Override
    public Set<Image<?>> install(@NonNull Artifact.Context context) throws IOException {
      return beneath(context)
          .spread(context.worlds().stream()
              .map(World::main)
              .collect(Collectors.toUnmodifiableSet()))
          .unpack(source(), artifactId().id().toString());
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
