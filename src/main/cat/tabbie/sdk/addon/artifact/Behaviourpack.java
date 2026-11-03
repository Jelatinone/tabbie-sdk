package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.revision.Chunk;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.minecraft.World;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.layout.Layout;
import cat.tabbie.sdk.platform.layout.Lease;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Vanilla Bedrock behaviour content. Alternative Bedrock plugin servers do not
 * support this artifact family. A World loads a behaviour pack only once its
 * {@link #CONFIGURATION_JSON_FILE_NAME list} names the pack by the identity
 * its {@link Descriptor.Manifest manifest} declares, so installing one also
 * lists it in each World it applies to. Uninstalling one reverses both: its
 * files are deleted, and its entry is removed from each list wherever the
 * entry is, whatever other packs did to the list since; the list itself
 * stays.
 */
@Registered
public sealed interface Behaviourpack extends Artifact, World.Content {

  /**
   * Name of the file in a World's namespace listing the behaviour packs the
   * World loads: a JSON array of each pack's {@code pack_id} and
   * {@code version}.
   */
  String CONFIGURATION_JSON_FILE_NAME = "world_behavior_packs.json";

  /**
   * An artifact unpacked into each World of its context, and listed there.
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
     * Unpacks the pack into each World of the context, into a directory of its
     * own, named by its artifact identity, beneath the World's directory
     * accepting behaviour packs, so two packs never collide; and appends the
     * pack's entry to each World's {@link #CONFIGURATION_JSON_FILE_NAME list}.
     * Only the folder holding the pack's manifest is unpacked, so a pack
     * wrapped in a folder of its own unpacks as if it were not. The list is
     * changed without being read, so a World without one has it established
     * by the operation, not by the pack.
     *
     * @param context selected target, the plate it is installed on, and the
     *                Worlds it applies to
     * @return validated immutable images
     * @throws Archive.Rejection.Malformed when the pack does not hold exactly
     *                                     one manifest, or its manifest
     *                                     declares no identity
     * @throws IOException                 when the pack cannot be captured or
     *                                     is refused
     * @throws Violation.Unplaced          when the context names no World, or
     *                                     a World has no directory accepting
     *                                     behaviour packs
     * @throws Violation                   when the model does not allow a
     *                                     World's list to be created and edited
     */
    @Override
    public Set<Image<?>> install(@NonNull Artifact.Context context) throws IOException {
      // Places are found before the pack is read, so a pack with nowhere to go is never captured
      Set<Layout.Directory> namespaces = context.worlds().stream()
          .map(World::namespace)
          .collect(Collectors.toUnmodifiableSet());
      Installer.Spread spread = beneath(context).spread(namespaces);

      List<Archive.Entry> entries = context.archive().inspect(source(), context.plate());
      Descriptor.Manifest manifest = Descriptor.Manifest.in(entries);
      List<Chunk> entry = List.of(manifest.listed());

      Set<Image<?>> files = spread.unpack(entries, manifest.root(), artifactId().id().toString());
      Set<Image<?>> listings = namespaces.stream()
          .<Image<?>>map(namespace -> namespace.configure(Address.of(CONFIGURATION_JSON_FILE_NAME),
              new Lease.Form.Json(), entry))
          .collect(Collectors.toUnmodifiableSet());
      return Stream.concat(files.stream(), listings.stream())
          .collect(Collectors.toUnmodifiableSet());
    }
  }
}
