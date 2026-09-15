package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import lombok.NonNull;

public sealed interface Artifact extends Store permits Mod {

  /**
   *
   * @return
   */
  @NonNull
  Identity<Artifact> artifactId();

  /**
   *
   * @return
   */
  @NonNull
  String artifactName();

  /**
   *
   * @return
   */
  long artifactSize();

  /**
   *
   * @return
   */
  @NonNull
  Set<Identity<Artifact>> depends();

  /**
   *
   * @return
   */
  @NonNull
  Set<Identity<Artifact>> conflicts();

  /**
   *
   * @return
   */
  @NonNull
  Set<Image<?>> images();

}
