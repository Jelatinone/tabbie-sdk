package cat.tabbie.sdk.merchant;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.model.Plate;
import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.layout.Node;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Captures content and describes the images that place it, on the
 * {@link Plate} its context names. Captures land in the plate's stash; no file
 * beneath the plate's root changes until the images are exposed, and
 * installers never launch processes. Whoever installs, such as the
 * interpreter or the command line, only supplies a context.
 *
 * @apiNote An install may run on a rehearsal plate: captures land in its stash
 *          as they would on an acting plate, anything else an installer does
 *          stays in the overlay, and the images it returns name files by
 *          address, so they expose on any plate of the same Cat.
 * @param <Contextual> accepted target context
 */
@FunctionalInterface
public interface Installer<Contextual extends Installer.Context> {

  /**
   * Captures the supplied content and describes the images that place it.
   *
   * @param context target context, naming the plate the install runs on
   * @return validated immutable images
   * @throws IOException when the content cannot be captured
   * @throws Violation   when the plate's tree does not allow the content where
   *                     it goes
   */
  Set<Image<?>> install(@NonNull Contextual context) throws IOException;

  /**
   * Selected target and the plate an install runs on. Installers find where
   * content goes in the plate's tree and capture into its stash.
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

    /**
     * Returns the plate the install runs on: its tree says where content goes,
     * and its stash keeps what the install captures.
     *
     * @return caller-owned plate
     */
    @NonNull
    Plate plate();

    /**
     * Returns the limits applied when unpacking archives.
     *
     * @return archive limits, {@link Archive#DEFAULT} by default
     */
    @NonNull
    default Archive archive() {
      return Archive.DEFAULT;
    }
  }

  /**
   * One install in progress: its context, and the directory of the context's
   * plate that its content goes beneath. Each call captures content into the
   * plate's stash and describes the images that place it, beneath the
   * directory or beneath directories named inside it, which need not exist
   * yet: their images create them, and they take their permissions from the
   * directory.
   *
   * @param context   context the install runs in
   * @param directory directory of the plate's tree the content goes beneath
   */
  record Install(@NonNull Context context, @NonNull Node.Directory directory) {

    /**
     * Places the source as one file named after it. The tree is checked before
     * the source is read, so content that cannot be placed is never captured.
     *
     * @param source content to place
     * @param into   names of the directories beneath this install's directory
     *               the file goes in, none for the directory itself
     * @return image creating the file
     * @throws IOException when the source cannot be captured
     * @throws Violation   when the tree does not allow the file there
     */
    public Set<Image<?>> file(@NonNull Describe source, @NonNull String... into) throws IOException {
      Address file = Address.of(into).resolve(source.fileName());
      directory.require(file);
      return Set.of(Image.create(directory, file, context.plate().capture(source)));
    }

    /**
     * Unpacks an archive within the context's limits.
     *
     * @param archive archive to unpack
     * @param into    names of the directories beneath this install's directory
     *                the archive is unpacked into, none for the directory
     *                itself
     * @return images creating the archive's files
     * @throws IOException when the archive cannot be captured or is refused
     * @throws Violation   when the tree does not allow a file where the
     *                     archive puts it
     */
    public Set<Image<?>> unpack(@NonNull Describe archive, @NonNull String... into) throws IOException {
      return context.archive().unpack(archive, directory, Address.of(into), context.plate());
    }
  }
}
