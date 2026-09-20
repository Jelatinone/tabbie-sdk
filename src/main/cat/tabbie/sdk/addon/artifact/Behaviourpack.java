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
 * Native Bedrock behaviour content with a separate manifest pack identity.
 * Alternative Bedrock plugin servers do not support this artifact family.
 */
public sealed interface Behaviourpack extends Artifact {

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
   * @param pack              manifest identity and declared version
   */
  record Default(
      @NonNull Identity<Artifact> artifactId,
      @NonNull String artifactName,
      @NonNull Reference artifactReference,
      @NonNull Store store,
      @NonNull Set<Label> labels,
      @NonNull Set<Identity<Artifact>> depends,
      @NonNull Set<Identity<Artifact>> conflicts) implements Behaviourpack {
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
      Artifact.validate(Behaviourpack.class, artifactId, artifactName, store, labels, depends, conflicts);
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
   * @param artifactName      nonblank display name
   * @param artifactReference named source content
   * @param store             caller-owned source store
   * @param labels            nonempty supported targets
   * @param depends           external dependencies
   * @param conflicts         external conflicts
   * @param pack              manifest identity and declared version
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
      @NonNull Artist<? super Behaviourpack> artist) implements Behaviourpack {
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
      Artifact.validate(Behaviourpack.class, artifactId, artifactName, store, labels, depends, conflicts);
    }

    @Override
    public Set<Image<?>> images(@NonNull Context context) throws IOException {
      return Image.fence(artist().paint(this, context));
    }
  }
}