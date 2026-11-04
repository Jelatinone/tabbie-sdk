package cat.tabbie.sdk.addon.artifact;

import static cat.tabbie.sdk.minecraft.Environment.CLIENT;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.revision.Chunk;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.meta.action.Install;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.attribute.Environments;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.layout.Lease;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Resource content, which changes how a game looks and sounds. Each edition
 * places and loads its packs in its own way, so Java and Bedrock packs are
 * separate families. An install makes a pack available to the client; turning
 * it on for a whole client is left to the player using it.
 */
public sealed interface Resourcepack extends Artifact {

  /**
   * Java resource content, placed as one file where a client's layout accepts
   * it. Servers deliver resource packs themselves, so it installs on clients
   * alone, and their layouts accept none.
   */
  @Registered
  @Distributions("java:base")
  @Environments(CLIENT)
  sealed interface Java extends Resourcepack {

    /**
     * Places the pack as one file named after its source, in the directory
     * of the Cat's model accepting Java resource packs, on every Java client.
     *
     * @param context the work, and where Java resource packs go in the Cat
     * @return validated immutable images
     * @throws IOException when the pack cannot be captured
     * @throws Violation   when the model does not allow the file, such as
     *                     {@link Violation.Unplaced} when no directory
     *                     accepts Java resource packs
     */
    @Install
    default Set<Image<?>> installRoot(@NonNull Install.Context context) throws IOException {
      return context.place().file(source());
    }

    /**
     * A Java resource pack as its provider describes it.
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
        @NonNull Set<Relation> relations) implements Java {
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

  /**
   * Bedrock resource content. Its files are available to the whole Cat, in
   * the directory its layout accepts them in. A distribution that loads packs
   * from folders turns a pack on in a World only once the World's
   * {@link #CONFIGURATION_JSON_FILE_NAME list} names it by the identity its
   * {@link Descriptor.Manifest manifest} declares, so installing one there
   * also lists it in each World it applies to. Uninstalling one reverses
   * both: its files are deleted, and its entry is removed from each list
   * wherever the entry is, whatever other packs did to the list since; the
   * list itself stays.
   */
  @Registered
  sealed interface Bedrock extends Resourcepack {

    /**
     * Name of the file in a World's namespace listing the resource packs the
     * World loads: a JSON array of each pack's {@code pack_id} and
     * {@code version}.
     */
    String CONFIGURATION_JSON_FILE_NAME = "world_resource_packs.json";

    /**
     * Unpacks the pack into a directory of its own, named by its artifact
     * identity, beneath the directory of the Cat's model accepting Bedrock
     * resource packs, and appends its entry to the
     * {@link #CONFIGURATION_JSON_FILE_NAME list} of each World, on vanilla
     * Bedrock and Endstone. Both load a pack from a folder, and send a World
     * only the packs its list names. Only the folder holding the pack's
     * manifest is unpacked, so a pack wrapped in a folder of its own unpacks
     * as if it were not. No World leaves the pack available, and on in no
     * World.
     *
     * @param context the work, its Worlds, and where Bedrock resource packs
     *                go in the Cat
     * @return validated immutable images
     * @throws Archive.Rejection.Malformed when the pack does not hold
     *                                     exactly one manifest, or its
     *                                     manifest declares no identity
     * @throws IOException                 when the pack cannot be captured
     *                                     or is refused
     * @throws Violation                   when the model does not allow the
     *                                     pack where it goes, or a World's
     *                                     list to be created and edited
     */
    @Install
    @Distributions({"bedrock:vanilla", "bedrock:endstone"})
    default Set<Image<?>> installListedRoot(@NonNull Install.Context context) throws IOException {
      List<Archive.Entry> entries = context.archive().inspect(source(), context.plate());
      Descriptor.Manifest manifest = Descriptor.Manifest.in(entries);
      List<Chunk> entry = List.of(manifest.listed());

      Set<Image<?>> files = context.place().unpack(entries, manifest.root(), artifactId().id().toString());
      Set<Image<?>> listings = context.worlds().stream()
          .<Image<?>>map(world -> world.namespace().configure(Address.of(CONFIGURATION_JSON_FILE_NAME),
              new Lease.Form.Json(), entry))
          .collect(Collectors.toUnmodifiableSet());
      return Stream.concat(files.stream(), listings.stream())
          .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Places the pack as the archive it is, named after its source, in the
     * directory of the Cat's model accepting Bedrock resource packs, on the
     * Nukkit family and Cloudburst Server, which load archives and send every
     * pack they hold. The manifest is read first, so content that is not one
     * pack is refused before anything is placed.
     *
     * @param context the work, and where Bedrock resource packs go in the Cat
     * @return validated immutable images
     * @throws Archive.Rejection.Malformed when the pack does not hold
     *                                     exactly one manifest, or its
     *                                     manifest declares no identity
     * @throws IOException                 when the pack cannot be captured
     *                                     or is refused
     * @throws Violation                   when the model does not allow the
     *                                     file, such as
     *                                     {@link Violation.Unplaced} when no
     *                                     directory accepts Bedrock resource
     *                                     packs
     */
    @Install
    @Distributions({
        "bedrock:cloudburst",
        "bedrock:cloudburst-nukkit",
        "bedrock:nukkit-mot",
        "bedrock:power-nukkit"})
    default Set<Image<?>> installArchivedRoot(@NonNull Install.Context context) throws IOException {
      Descriptor.Manifest.in(context.archive().inspect(source(), context.plate()));
      return context.place().file(source());
    }

    /**
     * A Bedrock resource pack as its provider describes it.
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
        @NonNull Set<Relation> relations) implements Bedrock {
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
}
