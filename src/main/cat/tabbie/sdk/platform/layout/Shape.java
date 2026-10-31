package cat.tabbie.sdk.platform.layout;

import java.util.List;
import java.util.Optional;

import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Reference;
import lombok.NonNull;

/**
 * The shape of a root, such as a Cat: a {@link File} or a {@link Directory} at
 * its own {@link Address}, beneath the directory standing for the root. Two
 * systems share this shape. A {@link Layout} is an immutable model of a root,
 * made up rather than backed, saying where content goes and what is allowed
 * there. A {@link Node} is what really exists beneath a root on a plate,
 * changed only through its leases. New content is described against a model,
 * and changes to existing content against nodes; both are described as
 * images.
 */
public sealed interface Shape permits Shape.File, Shape.Directory, Layout, Node {

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
   * Locates the place at an address relative to this one, following names one
   * component at a time. It goes to exactly that place or fails; it never
   * settles for another place along the way.
   *
   * @param tail address relative to this place
   * @return the place there, or this place for the root address
   * @throws Violation.Missing  when nothing is at a step
   * @throws Violation.Terminal when a step leads beneath a file
   */
  @NonNull
  default Shape locate(@NonNull Address tail) {
    Shape reached = this;
    for (String name : tail.components()) {
      reached = switch (reached) {
        case Shape.Directory directory -> directory.child(name)
            .orElseThrow(() -> new Violation.Missing(directory.address().resolve(name)));
        case Shape.File file -> throw new Violation.Terminal(file.address(), name);
      };
    }
    return reached;
  }

  /**
   * Locates the place at names beneath this one, as {@link #locate(Address)}
   * does.
   *
   * @param names single-name components, none for this place itself
   * @return the place there
   * @throws Violation.Missing        when nothing is at a step
   * @throws Violation.Terminal       when a step leads beneath a file
   * @throws IllegalArgumentException when a component is not a single portable
   *                                  filename
   */
  @NonNull
  default Shape locate(@NonNull String... names) {
    return locate(Address.of(names));
  }

  /**
   * A file. Nothing lies beneath a file.
   */
  sealed interface File extends Shape permits Layout.File, Node.File {
  }

  /**
   * A directory, holding the places directly beneath it.
   */
  sealed interface Directory extends Shape permits Layout.Directory, Node.Directory {

    /**
     * Lists the places directly beneath this directory.
     *
     * @return uniquely named places, possibly empty
     */
    @NonNull
    List<? extends Shape> children();

    /**
     * Finds the place of a name directly beneath this directory.
     *
     * @param name single filename
     * @return the place there, or empty when nothing of that name is here
     */
    @NonNull
    default Optional<? extends Shape> child(@NonNull String name) {
      return children().stream()
          .filter(child -> child.name().equals(name))
          .findFirst();
    }

    /**
     * Requires that a file may be created at a tail beneath this directory,
     * as this directory's own system says: a model checks its rules, and
     * what exists checks only that nothing is there yet.
     *
     * @param tail address of the file relative to this directory
     * @return this directory
     * @throws Violation                when the file may not be created there
     * @throws IllegalArgumentException when the tail names this directory
     */
    @NonNull
    Shape.Directory require(@NonNull Address tail);

    /**
     * Describes creating a file from retained content at a tail beneath this
     * directory, together with any directories missing above it, once
     * {@link #require(Address)} allows it.
     *
     * @param tail  address of the file relative to this directory
     * @param after retained content
     * @return image creating the file
     * @throws Violation                when the file may not be created there,
     *                                  as {@link #require(Address)} refuses
     * @throws IllegalArgumentException when the tail names this directory
     */
    @NonNull
    default Image<Image.File> create(@NonNull Address tail, @NonNull Reference.Captured after) {
      require(tail);
      return new Image.Installation(new Image.File.Absent(), new Image.File.Present(after), address().resolve(tail));
    }
  }
}
