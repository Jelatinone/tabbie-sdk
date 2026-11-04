package cat.tabbie.sdk.merchant;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.album.model.Plate;
import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.platform.layout.Layout;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Captures content and describes the images that place it. An install places
 * content against the model its context names alone, never reading the
 * Cat's files, and captures into the stash of the context's {@link Plate}.
 * Nothing beneath the Cat's root changes until the images are exposed, and
 * installers never launch processes. Whoever installs, such as the
 * interpreter or the command line, only supplies a context.
 *
 * <p>
 * A file other content also edits, such as the list a World keeps of the
 * packs it loads, is changed by a {@link Image.Configuration configuration}
 * anchored at the end or by content, so an install describes the change
 * without reading the file, and its preimage undoes it whatever other content
 * did to the file since. Uninstalling content exposes the preimages of what
 * its install described, and nothing else. When such a configuration's file
 * is absent, the operation adds the installation that makes
 * {@link Image.Configuration#sure sure} it exists: the operation owns the
 * file's existence, and the content only its change, so uninstalling the
 * content leaves the file in place.
 *
 * @apiNote An install may run on a rehearsal plate: captures land in its stash
 *          as they would on an acting plate, and the images it returns name
 *          files by address, so they expose on any plate of the same Cat.
 * @param <Contextual> accepted target context
 */
@FunctionalInterface
public interface Installer<Contextual extends Installer.Context> {

  /**
   * Captures the supplied content and describes the images that place it.
   *
   * @param context target context, naming the model to place against
   * @return validated immutable images
   * @throws IOException when the content cannot be captured
   * @throws Violation   when the model does not allow the content where it
   *                     goes
   */
  Set<Image<?>> install(@NonNull Contextual context) throws IOException;

  /**
   * Selected target, the model an install places content against, and the
   * plate whose stash keeps what it captures.
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
     * Returns the model the install places content against: the layout of
     * the Cat's release, with core's own layer and the Worlds the install
     * applies to mounted in it. Whatever already exists in the Cat is not the
     * model's to say.
     *
     * @return model declared from the Cat's root
     */
    @NonNull
    Layout.Directory model();

    /**
     * Returns the plate whose stash keeps what the install captures, and whose
     * scratch space unpacking uses. An install never goes through the plate's
     * tree.
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
   * One install of a type of content: its context, the type that says where
   * the content goes, and the places it goes. Each place is a directory of the
   * context's model accepting the type, found when the install is made, so
   * content with nowhere to go is refused before anything is read. Content of
   * the Cat has one place, the model's accepting directory; content of Worlds
   * has one beneath each World's namespace. Each call captures content into
   * the plate's stash once, and describes the images that place it at every
   * place, or beneath directories named inside each.
   *
   * @param context context the install runs in
   * @param type    type of the content, which says where it goes
   * @param places  directories of the context's model accepting the type, at
   *                least one
   */
  record Install(@NonNull Context context, @NonNull Class<?> type, @NonNull Set<Layout.Directory> places) {

    /**
     * Copies the places, and checks there is at least one and each accepts
     * the type.
     *
     * @throws IllegalArgumentException when there is no place, or a place does
     *                                  not accept the type
     */
    public Install {
      places = Set.copyOf(places);
      if (places.isEmpty()) {
        throw new IllegalArgumentException("An install places content in at least one directory.");
      }
      if (places.stream().anyMatch(place -> !place.accepts(type))) {
        throw new IllegalArgumentException("An install places content only in directories accepting its type.");
      }
    }

    /**
     * Creates an install placing content of the Cat, in the directory of the
     * context's model accepting the type.
     *
     * @param context context the install runs in
     * @param type    type of the content, which says where it goes
     * @throws Violation.Unplaced  when no directory accepts the type
     * @throws Violation.Ambiguous when several directories accept it
     */
    public Install(@NonNull Context context, @NonNull Class<?> type) {
      this(context, type, Set.of(context.model().find(type).unique()));
    }

    /**
     * Resolves an install placing content beneath each of several directories
     * of the context's model, in the directory beneath each accepting the
     * type, such as the datapacks directory of each World's namespace.
     *
     * @param context context the install runs in
     * @param type    type of the content, which says where it goes
     * @param roots   directories of the context's model to place beneath
     * @return install placing the content beneath every root
     * @throws Violation.Unplaced  when no root is given, or one accepts the
     *                             type nowhere
     * @throws Violation.Ambiguous when one accepts the type in several
     *                             directories
     */
    public static Install resolve(
        @NonNull Context context,
        @NonNull Class<?> type,
        @NonNull Set<Layout.Directory> roots) {
      if (roots.isEmpty()) {
        throw new Violation.Unplaced(type);
      }
      return new Install(context, type, roots.stream()
          .map(root -> root.find(type).unique())
          .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Places the source as one file named after it at every place. The model
     * is checked at every place before the source is read, so content that
     * cannot be placed is never captured.
     *
     * @param source content to place
     * @param into   names of the directories beneath each place the file goes
     *               in, none for the place itself
     * @return images creating the file, one for each place
     * @throws IOException when the source cannot be captured
     * @throws Violation   when the model does not allow the file at a place
     */
    public Set<Image<?>> file(@NonNull Describe source, @NonNull String... into) throws IOException {
      Address file = Address.of(into).resolve(source.fileName());
      places.forEach(place -> place.require(file));
      Reference.Captured captured = context.plate().capture(source);
      return places.stream()
          .<Image<?>>map(place -> place.create(file, captured))
          .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Unpacks an archive at every place within the context's limits,
     * inspecting it and capturing each of its files once.
     *
     * @param archive archive to unpack
     * @param into    names of the directories beneath each place the archive
     *                is unpacked into, none for the place itself
     * @return images creating the archive's files at every place
     * @throws IOException when the archive cannot be captured or is refused
     * @throws Violation   when the model does not allow a file where the
     *                     archive puts it
     */
    public Set<Image<?>> unpack(@NonNull Describe archive, @NonNull String... into) throws IOException {
      return context.archive().unpack(archive, context.model(), destinations(into), context.plate());
    }

    /**
     * Unpacks the inspected entries of an archive that lie beneath a folder of
     * it at every place, each at its path relative to that folder, so content
     * wrapped in a folder of its own unpacks as if it were not. Each file is
     * captured once.
     *
     * @param entries file entries of one archive, as its inspection found them
     * @param from    folder of the archive to unpack from, the root address for
     *                the whole archive
     * @param into    names of the directories beneath each place the files are
     *                unpacked into, none for the place itself
     * @return images creating the files at every place
     * @throws IOException              when the archive cannot be reopened or
     *                                  is refused
     * @throws Violation                when the model does not allow a file
     *                                  where it is unpacked
     * @throws IllegalArgumentException when no entry lies beneath the folder
     */
    public Set<Image<?>> unpack(
        @NonNull List<Archive.Entry> entries,
        @NonNull Address from,
        @NonNull String... into) throws IOException {
      return context.archive().unpack(entries, from, context.model(), destinations(into), context.plate());
    }

    /**
     * Addresses beneath every place, relative to the model's root.
     */
    private Set<Address> destinations(String... into) {
      return places.stream()
          .map(place -> place.address().resolve(into))
          .collect(Collectors.toUnmodifiableSet());
    }
  }
}
