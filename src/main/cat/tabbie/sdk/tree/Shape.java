package cat.tabbie.sdk.tree;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import lombok.NonNull;

/**
 * One place in a tree: a {@link File file} or a {@link Directory directory} at
 * its own {@link Address}, beneath the directory standing for the root. A
 * shape names a place whether or not anything is there; its
 * {@link #state() state} says what is there now. What backs a shape is its
 * kind's: a {@link Node} is what is beneath a root on a plate.
 */
public sealed interface Shape permits Shape.File, Shape.Directory, Node {

  /**
   * Returns where this place is.
   *
   * @return address beneath the root, the root address only for the directory
   *         standing for the root
   */
  @NonNull
  Address address();

  /**
   * Returns the directory directly above this place.
   *
   * @return parent directory, empty for the directory standing for the root
   */
  @NonNull
  Optional<? extends Shape.Directory> parent();

  /**
   * Returns this place's name, the last component of its address.
   *
   * @return single filename, empty only for the directory standing for the
   *         root
   */
  @NonNull
  default String name() {
    return address().fileName().orElse("");
  }

  /**
   * Reads what is at this place now, whatever kind of place this shape names:
   * nothing, a file, a directory, or something else. A place beneath a file
   * holds nothing.
   *
   * @return the place's state
   * @throws IOException when the place cannot be inspected
   */
  @NonNull
  State state() throws IOException;

  /**
   * A file, possibly absent. Nothing lies beneath a file.
   */
  sealed interface File extends Shape permits Node.File {
  }

  /**
   * A directory, possibly absent, holding the places beneath it.
   */
  sealed interface Directory extends Shape permits Node.Directory {

    /**
     * Lists what exists directly beneath this directory.
     *
     * @return uniquely named places, empty when this directory is absent
     * @throws IOException when the directory cannot be listed
     */
    @NonNull
    List<? extends Shape> children() throws IOException;

    /**
     * Finds what exists at a name directly beneath this directory, matching
     * the name's case exactly.
     *
     * @param name single filename
     * @return the place there, or empty when nothing of that name is here
     * @throws IOException when the directory cannot be listed
     */
    @NonNull
    default Optional<? extends Shape> child(@NonNull String name) throws IOException {
      return children().stream()
          .filter(child -> child.name().equals(name))
          .findFirst();
    }

    /**
     * Names the file at a tail beneath this directory, whatever is there now.
     *
     * @param tail address relative to this directory, beneath it
     * @return the file there, possibly absent
     * @throws IllegalArgumentException when the tail names this directory
     */
    @NonNull
    Shape.File file(@NonNull Address tail);

    /**
     * Names the file at names beneath this directory, as
     * {@link #file(Address)} does.
     *
     * @param names single-name components, at least one
     * @return the file there, possibly absent
     * @throws IllegalArgumentException when no name is given, or a name is
     *                                  not a portable single filename
     */
    @NonNull
    default Shape.File file(@NonNull String... names) {
      return file(Address.of(names));
    }

    /**
     * Names the directory at a tail beneath this directory, whatever is there
     * now.
     *
     * @param tail address relative to this directory, the root address for
     *             this directory itself
     * @return the directory there, possibly absent
     */
    @NonNull
    Shape.Directory directory(@NonNull Address tail);

    /**
     * Names the directory at names beneath this directory, as
     * {@link #directory(Address)} does.
     *
     * @param names single-name components, none for this directory itself
     * @return the directory there, possibly absent
     * @throws IllegalArgumentException when a name is not a portable single
     *                                  filename
     */
    @NonNull
    default Shape.Directory directory(@NonNull String... names) {
      return directory(Address.of(names));
    }
  }
}
