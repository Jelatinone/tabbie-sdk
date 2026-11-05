package cat.tabbie.sdk.addon.artifact;

import static cat.tabbie.sdk.meta.action.Closure.NAMESPACES;

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
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.layout.Lease;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Bedrock behaviour content, which changes how a game plays. Each
 * distribution loads packs in its own way, so a pack installs in one of two:
 * <ul>
 * <li>vanilla Bedrock and Endstone load a behaviour pack from a World, and
 * only once the World's {@link #CONFIGURATION_JSON_FILE_NAME list} names it
 * by the identity its {@link Descriptor.Manifest manifest} declares, so the
 * pack is unpacked into each World it applies to and listed there;</li>
 * <li>Nukkit-MOT loads every behaviour pack it holds for the whole Cat, from
 * one directory, so the pack is unpacked there alone.</li>
 * </ul>
 * The other Bedrock servers load none, so no install applies to them.
 * Uninstalling a pack reverses its install: its files are deleted, and its
 * entry is removed from each list wherever the entry is, whatever other packs
 * did to the list since; the list itself stays.
 */
@Registered
public sealed interface Behaviourpack extends Artifact {

  /**
   * Name of the file in a World's namespace listing the behaviour packs the
   * World loads: a JSON array of each pack's {@code pack_id} and
   * {@code version}.
   */
  String CONFIGURATION_JSON_FILE_NAME = "world_behavior_packs.json";

  /**
   * Unpacks the pack into each World, into a directory of its own, named by
   * its artifact identity, beneath the World's directory accepting behaviour
   * packs, so two packs never collide; and appends the pack's entry to each
   * World's {@link #CONFIGURATION_JSON_FILE_NAME list}, on vanilla Bedrock
   * and Endstone. Only the folder holding the pack's manifest is unpacked, so
   * a pack wrapped in a folder of its own unpacks as if it were not. The list
   * is changed without being read, so a World without one has it established
   * by the operation, not by the pack.
   *
   * @param context the work, its Worlds, and where behaviour packs go in
   *                every World
   * @return validated immutable images
   * @throws Archive.Rejection.Malformed when the pack does not hold exactly
   *                                     one manifest, or its manifest
   *                                     declares no identity
   * @throws IOException                 when the pack cannot be captured or
   *                                     is refused
   * @throws Violation                   when the model does not allow the
   *                                     pack in a World, or a World's list to
   *                                     be created and edited
   */
  @Install(NAMESPACES)
  @Distributions({"bedrock:vanilla", "bedrock:endstone"})
  default Set<Image<?>> installListedNamespaces(@NonNull Install.Context context) throws IOException {
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
   * identity, beneath the directory of the Cat's model accepting behaviour
   * packs, on Nukkit-MOT. It loads every behaviour pack it holds for the
   * whole Cat, so no World is needed and none lists the pack. Only the folder
   * holding the pack's manifest is unpacked, so a pack wrapped in folders of
   * its own unpacks as if it were not. The manifest is read first, so a pack
   * the server could not load is refused before anything is placed.
   *
   * @param context the work, and where behaviour packs go in the Cat
   * @return validated immutable images
   * @throws Archive.Rejection.Malformed   when the pack does not hold exactly
   *                                       one manifest, or its manifest
   *                                       declares no identity
   * @throws Archive.Rejection.Unsupported when the manifest writes the pack's
   *                                       version as a string
   * @throws IOException                   when the pack cannot be captured
   *                                       or is refused
   * @throws Violation                     when the model does not allow the
   *                                       pack where it goes, such as
   *                                       {@link Violation.Unplaced} when no
   *                                       directory accepts behaviour packs
   */
  @Install
  @Distributions("bedrock:nukkit-mot")
  default Set<Image<?>> installUnpackedRoot(@NonNull Install.Context context) throws IOException {
    List<Archive.Entry> entries = context.archive().inspect(source(), context.plate());
    Descriptor.Manifest manifest = Descriptor.Manifest.in(entries)
        .requireNumbered();
    return context.place().unpack(entries, manifest.root(), artifactId().id().toString());
  }

  /**
   * A behaviour pack as its provider describes it.
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
      Artifact.validate(coordinates, artifactName, labels, relations);
    }
  }
}
