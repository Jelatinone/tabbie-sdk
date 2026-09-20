package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Reference;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Java world data content. A caller supplies the selected world's layout;
 * constructors do not inspect pack metadata or discover world directories.
 */
public sealed interface Datapack extends Artifact {
  /**
   * An artifact using the shared named-file and ZIP capture artist.
   *
   * @param artifactId        stable catalog artifact identity
   * @param artifactName      non-blank display name
   * @param artifactReference named source content
   * @param store             caller-owned source store
   * @param labels            nonempty supported targets
   * @param depends           external dependencies
   * @param conflicts         external conflicts
   */
  record Default(
      @NonNull Identity<Artifact> artifactId,
      @NonNull String artifactName,
      @NonNull Reference artifactReference,
      @NonNull Store store,
      @NonNull Set<Label> labels,
      @NonNull Set<Identity<Artifact>> depends,
      @NonNull Set<Identity<Artifact>> conflicts) implements Datapack {
    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relationships are
     *                                  invalid
     */
    public Default {
      labels = Set.copyOf(labels);
      depends = Set.copyOf(depends);
      conflicts = Set.copyOf(conflicts);
      validate();
    }

    @Override
    public Set<Image<?>> images(@NonNull Context context) throws IOException {
      return Image.fence(Artifact.DEFAULT_ARTIST.paint(this, context));
    }
  }

  /**
   * An artifact whose content images are described by a caller-supplied artist.
   *
   * @param artifactId        stable catalog artifact identity
   * @param artifactName      non-blank display name
   * @param artifactReference named source content
   * @param store             caller-owned source store
   * @param labels            nonempty supported targets
   * @param depends           external dependencies
   * @param conflicts         external conflicts
   * @param artist            typed content description strategy
   */
  record Custom(
      @NonNull Identity<Artifact> artifactId,
      @NonNull String artifactName,
      @NonNull Reference artifactReference,
      @NonNull Store store,
      @NonNull Set<Label> labels,
      @NonNull Set<Identity<Artifact>> depends,
      @NonNull Set<Identity<Artifact>> conflicts,
      @NonNull Artist<? super Datapack> artist) implements Datapack {

    /**
     * Copies collections and checks local declarations without reading content.
     *
     * @throws IllegalArgumentException when declared support or relationships are
     *                                  invalid
     */
    public Custom {
      labels = Set.copyOf(labels);
      depends = Set.copyOf(depends);
      conflicts = Set.copyOf(conflicts);
      validate();
    }

    @Override
    public Set<Image<?>> images(@NonNull Context context) throws IOException {
      return Image.fence(artist().paint(this, context));
    }
  }
}