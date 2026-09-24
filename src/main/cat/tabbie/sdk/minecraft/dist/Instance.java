package cat.tabbie.sdk.minecraft.dist;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Intermediate;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

public interface Instance extends Release.Payload<Installer.Context> {

  /**
   * Stable artifact identity derived from the file coordinates, so equal
   * coordinates always yield the same identity across processes.
   *
   * @return artifact identity
   */
  default Identity<Instance> artifactId() {
    return Identity.create(coordinates().canonical());
  }

  /**
   * Human-readable canonical artifact name
   *
   * @return artifact name
   */
  @NonNull
  String artifactName();

  /**
   * Delegated storage receiving capture/open calls
   *
   * @return delegated storage
   */
  Store store();

  /**
   * Declares support for the entire artifact.
   *
   * @return immutable, nonempty supported targets
   */
  @NonNull
  Set<Label> labels();

  /**
   * Allocate and initialize (starts) the instance
   *
   * @return void
   * @throws IOException when capture or layout resolution fails
   */
  @NonNull
  Intermediate<Void> allocate() throws IOException;

  /**
   * Deallocate and deinitialize (stops) the instance
   *
   * @return void
   * @throws IOException when capture or layout resolution fails
   */
  @NonNull
  Intermediate<Void> deallocate() throws IOException;

  /**
   * Reallocate and reinitialize (restart) the instance
   *
   * @return void
   * @throws IOException when capture or layout resolution fails
   */
  @NonNull
  Intermediate<Void> reallocate() throws IOException;
}
