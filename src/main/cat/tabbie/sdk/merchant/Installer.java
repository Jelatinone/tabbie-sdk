package cat.tabbie.sdk.merchant;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.repository.Store;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import lombok.NonNull;

/**
 * Describes content without applying it.
 *
 * @param <Payload> accepted artifact family
 */
@FunctionalInterface
public interface Installer<Contextual extends Installer.Context> {

  /**
   * Captures and describes the supplied content.
   *
   * @param payload source payload
   * @param context target context
   * @return described images
   * @throws IOException when capture or layout resolution fails
   */
  Intermediate<Set<Image<?>>> install(@NonNull Contextual context) throws IOException;

  /**
   * Logical target mounts and caller-owned retention, independent of physical
   * paths.
   */
  public interface Context {

    /**
     * Returns caller-owned retention store.
     *
     * @return caller-owned retention store
     */
    @NonNull
    <S extends Store & Extract> S store();

    /**
     * Returns mount relative to the Den, empty for its working directory.
     *
     * @return mount relative to the Den, empty for its working directory
     */
    @NonNull
    Path contextRoot();
  }
}
