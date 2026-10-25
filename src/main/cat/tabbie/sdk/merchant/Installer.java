package cat.tabbie.sdk.merchant;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.layout.Address;
import lombok.NonNull;

/**
 * Describes content without applying it. Installers describe captures of
 * content and the images that place it; the captures run in the stash of the
 * {@link cat.tabbie.sdk.album.model.Plate Plate} the result is collapsed on.
 * They never write to the Cat's files or launch processes.
 *
 * @param <Contextual> accepted target context
 */
@FunctionalInterface
public interface Installer<Contextual extends Installer.Context> {

  /**
   * Captures and describes the supplied content.
   *
   * @param context target context
   * @return unevaluated capture producing validated images
   * @throws IOException when layout resolution fails
   */
  Intermediate<Set<Image<?>>> install(@NonNull Contextual context) throws IOException;

  /**
   * Selected target and World, independent of physical paths. A context carries
   * no stash: captures run on the plate that collapses the installer's result.
   */
  interface Context {

    /**
     * Returns the explicitly selected target.
     *
     * @return selected target
     */
    @NonNull
    Label label();

    /**
     * Returns the platform of the machine being installed to, for selecting
     * native content.
     *
     * @return target platform
     */
    @NonNull
    Platform platform();

    /**
     * Returns the selected World's directory beneath the Cat. Content that
     * belongs to a World is placed in it and {@link Address#require(Address)
     * required} there. Callers that have not selected a World must reject
     * World content themselves rather than name an arbitrary directory.
     *
     * @return World directory
     */
    @NonNull
    Address world();
  }
}
