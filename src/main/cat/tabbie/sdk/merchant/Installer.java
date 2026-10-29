package cat.tabbie.sdk.merchant;

import java.io.IOException;
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
   * One install of a type of content: its context, and the type that says
   * where the content goes. Its place is the directory of the context's model
   * that accepts the type, found when the content is placed;
   * {@link #spread(Set)} places the content across several directories
   * instead. Each call captures content into the plate's stash and describes
   * the images that place it, at the place or beneath directories named
   * inside it.
   *
   * @param context context the install runs in
   * @param type    type of the content, which says where it goes
   */
  record Install(@NonNull Context context, @NonNull Class<?> type) {

    /**
     * Places the source as one file named after it. The model is checked
     * before the source is read, so content that cannot be placed is never
     * captured.
     *
     * @param source content to place
     * @param into   names of the directories beneath the place the file goes
     *               in, none for the place itself
     * @return image creating the file
     * @throws IOException when the source cannot be captured
     * @throws Violation   when no directory accepts the type, several do, or
     *                     the model does not allow the file there
     */
    public Set<Image<?>> file(@NonNull Describe source, @NonNull String... into) throws IOException {
      Layout.Directory place = place();
      Address file = Address.of(into).resolve(source.fileName());
      place.require(file);
      return Set.of(place.create(file, context.plate().capture(source)));
    }

    /**
     * Unpacks an archive at the place within the context's limits.
     *
     * @param archive archive to unpack
     * @param into    names of the directories beneath the place the archive
     *                is unpacked into, none for the place itself
     * @return images creating the archive's files
     * @throws IOException when the archive cannot be captured or is refused
     * @throws Violation   when no directory accepts the type, several do, or
     *                     the model does not allow a file where the archive
     *                     puts it
     */
    public Set<Image<?>> unpack(@NonNull Describe archive, @NonNull String... into) throws IOException {
      return context.archive().unpack(archive, place(), Address.of(into), context.plate());
    }

    /**
     * Spreads this install across directories of the context's model, placing
     * the content in each at the directory beneath it that accepts the type,
     * such as each World an addon applies to.
     *
     * @param directories directories of the context's model to spread across
     * @return install across the directories
     * @throws Violation.Unplaced  when no directory is given, or one accepts
     *                             the type nowhere
     * @throws Violation.Ambiguous when one accepts the type in several
     *                             directories
     */
    @NonNull
    public Spread spread(@NonNull Set<Layout.Directory> directories) {
      if (directories.isEmpty()) {
        throw new Violation.Unplaced(type);
      }
      return new Spread(context, directories.stream()
          .map(directory -> directory.find(type).unique())
          .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Finds the directory of the context's model that accepts the type.
     */
    private Layout.Directory place() {
      return context.model().find(type).unique();
    }
  }

  /**
   * An install spread over several places of its context's model at once.
   * Each call captures content into the plate's stash once and describes the
   * images that place it at every place, or beneath directories named inside
   * each.
   *
   * @param context context the install runs in
   * @param places  directories of the context's model to place the content
   *                at, at least one
   */
  record Spread(@NonNull Context context, @NonNull Set<Layout.Directory> places) {

    /**
     * Copies the places and checks there is at least one.
     *
     * @throws IllegalArgumentException when there is no place
     */
    public Spread {
      places = Set.copyOf(places);
      if (places.isEmpty()) {
        throw new IllegalArgumentException("An install spreads over at least one place.");
      }
    }

    /**
     * Places the source as one file named after it at every place. The model
     * is checked at every place before the source is read.
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
      return context.archive().unpack(archive, context.model(), places.stream()
          .map(place -> place.address().resolve(into))
          .collect(Collectors.toUnmodifiableSet()), context.plate());
    }
  }
}
