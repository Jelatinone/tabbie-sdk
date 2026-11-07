package cat.tabbie.sdk.content;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.meta.action.Closure;
import cat.tabbie.sdk.model.Layout;
import cat.tabbie.sdk.model.Violation;
import cat.tabbie.sdk.model.World;
import cat.tabbie.sdk.plate.Archive;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.revision.Image;
import lombok.NonNull;

/**
 * Captures content and describes the images that place it. An install places
 * content against the model its work names alone, never reading the Cat's
 * files, and captures through the work's {@link Capture}. Nothing beneath the
 * Cat's root changes until the images are exposed, and installers never
 * launch processes. Whoever installs, such as the interpreter or the command
 * line, only supplies the work.
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
 * @apiNote An install may run while core rehearses: captures land in the
 *          stash as they would while acting, and the images it returns name
 *          files by address, so they expose on any plate of the same Cat.
 * @param <Contextual> accepted work
 */
@FunctionalInterface
public interface Installer<Contextual extends Work> {

  /**
   * Captures the supplied content and describes the images that place it.
   *
   * @param context the work, naming the model to place against
   * @return validated immutable images
   * @throws IOException when the content cannot be captured
   * @throws Violation   when the model does not allow the content where it
   *                     goes
   */
  Set<Image<?>> install(@NonNull Contextual context) throws IOException;

  /**
   * One install of a type of content: its work, the type that says where the
   * content goes, and the places it goes. Each place is a directory of the
   * work's model accepting the type, found when the install is made, so
   * content with nowhere to go is refused before anything is read. Content of
   * the Cat has one place, the model's accepting directory; content of Worlds
   * has one beneath each World's namespace. Each call captures content through
   * the work's capture once, and describes the images that place it at every
   * place, or beneath directories named inside each.
   *
   * @param work   work the install runs in
   * @param type   type of the content, which says where it goes
   * @param places directories of the work's model accepting the type, at least
   *               one
   */
  record Install(@NonNull Work work, @NonNull Class<?> type, @NonNull Set<Layout.Directory> places) {

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
     * work's model accepting the type.
     *
     * @param work work the install runs in
     * @param type type of the content, which says where it goes
     * @throws Violation.Unplaced  when no directory accepts the type
     * @throws Violation.Ambiguous when several directories accept it
     */
    public Install(@NonNull Work work, @NonNull Class<?> type) {
      this(work, type, Set.of(work.model().find(type).unique()));
    }

    /**
     * Resolves where an action acting where its closure says places content
     * of a type: in the directory of the work's model accepting it, at the
     * root; or in the directory accepting it beneath the namespace of each
     * World the work names, in namespaces. Every action acting in places
     * resolves its closure here.
     *
     * @param work    work the action runs in
     * @param type    type of the content, which says where it goes
     * @param closure where the action acts
     * @return the install placing the content
     * @throws Violation.Unplaced  when no directory accepts the type, or the
     *                             action acts in namespaces and the work names
     *                             no World
     * @throws Violation.Ambiguous when several directories accept it at the
     *                             root, or beneath one namespace
     */
    public static Install of(@NonNull Artifact.Context work, @NonNull Class<?> type, @NonNull Closure closure) {
      return switch (closure) {
        case ROOT -> new Install(work, type);
        case NAMESPACES -> resolve(work, type, work.worlds().stream()
            .map(World::namespace)
            .collect(Collectors.toUnmodifiableSet()));
      };
    }

    /**
     * Resolves an install placing content beneath each of several directories
     * of the work's model, in the directory beneath each accepting the type,
     * such as the datapacks directory of each World's namespace.
     *
     * @param work  work the install runs in
     * @param type  type of the content, which says where it goes
     * @param roots directories of the work's model to place beneath
     * @return install placing the content beneath every root
     * @throws Violation.Unplaced  when no root is given, or one accepts the
     *                             type nowhere
     * @throws Violation.Ambiguous when one accepts the type in several
     *                             directories
     */
    public static Install resolve(
        @NonNull Work work,
        @NonNull Class<?> type,
        @NonNull Set<Layout.Directory> roots) {
      if (roots.isEmpty()) {
        throw new Violation.Unplaced(type);
      }
      return new Install(work, type, roots.stream()
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
      return file(source, Address.of(into).resolve(source.fileName()));
    }

    /**
     * Places the source as one file at an address beneath every place, so
     * content can be renamed as it is placed, such as an archived pack named
     * after its identity. The model is checked at every place before the
     * source is read, so content that cannot be placed is never captured.
     *
     * @param source content to place
     * @param file   address of the file beneath each place
     * @return images creating the file, one for each place
     * @throws IOException when the source cannot be captured
     * @throws Violation   when the model does not allow the file at a place
     */
    public Set<Image<?>> file(@NonNull Describe source, @NonNull Address file) throws IOException {
      places.forEach(place -> place.require(file));
      Reference.Captured captured = work.capture().capture(source);
      return places.stream()
          .<Image<?>>map(place -> place.create(file, captured))
          .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Unpacks an archive at every place within core's limits, inspecting it
     * and capturing each of its files once.
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
      return work.capture().unpack(archive, work.model(), destinations(into));
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
      return work.capture().unpack(entries, from, work.model(), destinations(into));
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
