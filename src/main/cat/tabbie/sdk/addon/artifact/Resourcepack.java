package cat.tabbie.sdk.addon.artifact;

import static cat.tabbie.sdk.minecraft.distribution.Bedrock.Manager.Of.ENDSTONE;
import static cat.tabbie.sdk.minecraft.distribution.Bedrock.Of.NATIVE;

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
   * it. Servers deliver resource packs themselves, so their layouts accept
   * none.
   */
  sealed interface Java extends Resourcepack {

    /**
     * An artifact placed as one file by the shared installer.
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
        Artifact.validate(Java.class, coordinates, artifactName, labels, relations);
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
        @NonNull Installer<Artifact.Context> installer) implements Java, Artifact.Custom {
      /**
       * Copies collections and checks local declarations without reading content.
       *
       * @throws IllegalArgumentException when declared support or relations are
       *                                  invalid
       */
      public Custom {
        labels = Set.copyOf(labels);
        relations = Set.copyOf(relations);
        Artifact.validate(Java.class, coordinates, artifactName, labels, relations);
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
  sealed interface Bedrock extends Resourcepack {

    /**
     * Name of the file in a World's namespace listing the resource packs the
     * World loads: a JSON array of each pack's {@code pack_id} and
     * {@code version}.
     */
    String CONFIGURATION_JSON_FILE_NAME = "world_resource_packs.json";

    /**
     * An artifact placed as its context's distribution loads it, and listed
     * in each World of its context where the distribution lists packs.
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
        Artifact.validate(Bedrock.class, coordinates, artifactName, labels, relations);
      }

      /**
       * Places the pack beneath the directory of the context's model
       * accepting Bedrock resource packs, as the context's distribution
       * loads it. Native Bedrock and Endstone load a pack from a folder, and
       * send a World only the packs its
       * {@link #CONFIGURATION_JSON_FILE_NAME list} names: the pack is
       * unpacked into a directory of its own, named by its artifact identity,
       * from the folder holding its manifest, and its entry is appended to
       * the list of each World the context names. The Nukkit family loads
       * only archives, and sends every pack it holds, so the pack is placed
       * there as the archive it is; so is it for any other distribution. The
       * manifest is read first either way, so content that is not one pack
       * is refused before anything is placed. A context naming no World
       * leaves the pack available, and on in no World.
       *
       * @param context selected target, the plate it is installed on, and
       *                the Worlds it applies to
       * @return validated immutable images
       * @throws Archive.Rejection.Malformed when the pack does not hold
       *                                     exactly one manifest, or its
       *                                     manifest declares no identity
       * @throws IOException                 when the pack cannot be captured
       *                                     or is refused
       * @throws Violation.Unplaced          when no directory of the model
       *                                     accepts Bedrock resource packs
       * @throws Violation                   when the model does not allow
       *                                     the pack where it goes, or a
       *                                     World's list to be created and
       *                                     edited
       */
      @Override
      public Set<Image<?>> install(@NonNull Artifact.Context context) throws IOException {
        List<Archive.Entry> entries = context.archive().inspect(source(), context.plate());
        Descriptor.Manifest manifest = Descriptor.Manifest.in(entries);
        boolean listing = switch (context.label().distribution()) {
          // Native Bedrock and Endstone load folders
          case NATIVE, ENDSTONE -> true;
          // The Nukkit family loads archives and sends every one
          default -> false;
        };
        if (!listing) {
          return beneath(context).file(source());
        }

        List<Chunk> entry = List.of(manifest.listed());
        Set<Image<?>> files = beneath(context).unpack(entries, manifest.root(), artifactId().id().toString());
        Set<Image<?>> listings = context.worlds().stream()
            .<Image<?>>map(world -> world.namespace().configure(Address.of(CONFIGURATION_JSON_FILE_NAME),
                new Lease.Form.Json(), entry))
            .collect(Collectors.toUnmodifiableSet());
        return Stream.concat(files.stream(), listings.stream())
            .collect(Collectors.toUnmodifiableSet());
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
        @NonNull Installer<Artifact.Context> installer) implements Bedrock, Artifact.Custom {
      /**
       * Copies collections and checks local declarations without reading content.
       *
       * @throws IllegalArgumentException when declared support or relations are
       *                                  invalid
       */
      public Custom {
        labels = Set.copyOf(labels);
        relations = Set.copyOf(relations);
        Artifact.validate(Bedrock.class, coordinates, artifactName, labels, relations);
      }
    }
  }
}
