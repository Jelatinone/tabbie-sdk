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
   * the directory its layout accepts them in. Each distribution loads packs
   * in its own way, so a pack installs in one of three:
   * <ul>
   * <li>vanilla Bedrock and Endstone load a pack from a folder, and send a
   * World only the packs its {@link #CONFIGURATION_JSON_FILE_NAME list}
   * names, by the identity the pack's {@link Descriptor.Manifest manifest}
   * declares, so the pack is unpacked and listed in each World it applies
   * to;</li>
   * <li>Nukkit-MOT and Cloudburst Server load a pack from a folder, and send
   * every pack they hold, so the pack is unpacked alone;</li>
   * <li>Cloudburst Nukkit and PowerNukkitX load only archives, and send
   * every pack they hold, so the pack is placed as the archive it is.</li>
   * </ul>
   * None of the last four reads a pack whose manifest writes its version as
   * a string, so such a pack is refused there before anything is placed.
   * Uninstalling a pack reverses its install: its files are deleted, and its
   * entry is removed from each list wherever the entry is, whatever other
   * packs did to the list since; the list itself stays.
   */
  @Registered
  sealed interface Bedrock extends Resourcepack {

    /**
     * Name of the file in a World's namespace listing the resource packs the
     * World loads: a JSON array of each pack's {@code pack_id} and
     * {@code version}. An entry names no subpack, so a pack offering
     * subpacks loads its base pack, and players choose a subpack themselves.
     * A subpack is part of its pack, not an artifact of its own.
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
     * Unpacks the pack into a directory of its own, named by its artifact
     * identity, beneath the directory of the Cat's model accepting Bedrock
     * resource packs, on Nukkit-MOT and Cloudburst Server. Both load a pack
     * from a folder and send every pack they hold, so no World lists it. Only
     * the folder holding the pack's manifest is unpacked, so a pack wrapped
     * in folders of its own unpacks as if it were not, however deep. The
     * manifest is read first, so a pack neither could load is refused before
     * anything is placed.
     *
     * @param context the work, and where Bedrock resource packs go in the Cat
     * @return validated immutable images
     * @throws Archive.Rejection.Malformed   when the pack does not hold
     *                                       exactly one manifest, or its
     *                                       manifest declares no identity
     * @throws Archive.Rejection.Unsupported when the manifest writes the
     *                                       pack's version as a string
     * @throws IOException                   when the pack cannot be captured
     *                                       or is refused
     * @throws Violation                     when the model does not allow
     *                                       the pack where it goes
     */
    @Install
    @Distributions({"bedrock:cloudburst", "bedrock:nukkit-mot"})
    default Set<Image<?>> installUnpackedRoot(@NonNull Install.Context context) throws IOException {
      List<Archive.Entry> entries = context.archive().inspect(source(), context.plate());
      Descriptor.Manifest manifest = Descriptor.Manifest.in(entries)
          .requireNumbered();
      return context.place().unpack(entries, manifest.root(), artifactId().id().toString());
    }

    /**
     * Places the pack as the archive it is, named by its artifact identity as
     * {@code <id>.mcpack}, in the directory of the Cat's model accepting
     * Bedrock resource packs, on Cloudburst Nukkit and PowerNukkitX. Both load
     * only archives, find a manifest at an archive's root or one folder deep,
     * and send every pack they hold. The name has the extension Cloudburst
     * Nukkit matches exactly, and two packs sharing a file name never collide.
     * The manifest is read first, so a pack neither could load is refused
     * before anything is placed.
     *
     * @param context the work, and where Bedrock resource packs go in the Cat
     * @return validated immutable images
     * @throws Archive.Rejection.Malformed   when the pack does not hold
     *                                       exactly one manifest, or its
     *                                       manifest declares no identity
     * @throws Archive.Rejection.Unsupported when the manifest lies more than
     *                                       one folder deep, or writes the
     *                                       pack's version as a string
     * @throws IOException                   when the pack cannot be captured
     *                                       or is refused
     * @throws Violation                     when the model does not allow
     *                                       the file, such as
     *                                       {@link Violation.Unplaced} when
     *                                       no directory accepts Bedrock
     *                                       resource packs
     */
    @Install
    @Distributions({"bedrock:cloudburst-nukkit", "bedrock:power-nukkit"})
    default Set<Image<?>> installArchivedRoot(@NonNull Install.Context context) throws IOException {
      Descriptor.Manifest.in(context.archive().inspect(source(), context.plate()))
          .requireDepth(1)
          .requireNumbered();
      return context.place().file(source(), Address.of(String.format("%s.mcpack", artifactId().id())));
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
