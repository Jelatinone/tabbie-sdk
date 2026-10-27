package cat.tabbie.sdk.merchant;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Platform;
import lombok.NonNull;

/**
 * Describes content without applying it. Installers describe captures of
 * content and the images that place it; the captures run in the stash of the
 * {@link cat.tabbie.sdk.album.model.Plate Plate} the result is collapsed on.
 * They never write files or launch processes.
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
   * @throws IOException when the content cannot be described
   */
  Intermediate<Set<Image<?>>> install(@NonNull Contextual context) throws IOException;

  /**
   * Selected target, independent of physical paths. A context carries no stash
   * and no tree: captures run on the plate that collapses the installer's
   * result, and installers find where content goes in that plate's tree.
   */
  // TODO: Selecting a World needs the World class. This context named the
  // selected World's directory through world(), an Address beneath the same
  // root as the installer's images; Distribution.layout resolved datapacks and
  // behaviour packs beneath it, and Artifact.Context#layout required them to
  // lie inside it. Installers should reach a World through the plate's tree
  // with find and locate instead, rather than through the context.
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
  }
}
