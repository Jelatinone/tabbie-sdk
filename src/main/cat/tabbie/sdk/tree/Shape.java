package cat.tabbie.sdk.tree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.form.Form;
import lombok.NonNull;

/**
 * One place in a tree: a {@link File file} or a {@link Directory directory} at
 * its own {@link Address}, beneath the directory standing for the root. A
 * shape names a place whether or not anything is there; its
 * {@link #state() state} says what is there now. A shape is anonymous: it has
 * no format until something {@link #as(Form.Factory) interprets} it. What
 * backs a shape is its kind's: a {@link Node} is what is beneath a root on a
 * plate, an {@link Entry} a place inside an archive, and a {@link Source} what
 * its maker declares.
 */
public sealed interface Shape permits Shape.File, Shape.Directory, Node, Entry, Source {

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
   * Returns the permissions that hold at this place: what its leases grant,
   * and what changes may be described here.
   *
   * @return immutable permissions, possibly empty
   */
  @NonNull
  Set<Rule.Permission> permissions();

  /**
   * Whether a permission holds at this place.
   *
   * @param permission permission to check
   * @return true when it holds
   */
  default boolean permits(@NonNull Rule.Permission permission) {
    return permissions().contains(permission);
  }

  /**
   * Interprets this place in a form, the one hop from a place to its format,
   * such as {@code source.as(Archive::of)}. The factory reads only what it
   * needs to know the place is in its format.
   *
   * @param <Interpreted> the form's type
   * @param factory       makes the form from this place
   * @return the form, bound to this place
   * @throws Refusal     with a {@link Reason.Content} when the place is not in
   *                     the form, or is but cannot be read
   * @throws IOException when the place cannot be read
   */
  @NonNull
  default <Interpreted extends Form> Interpreted as(@NonNull Form.Factory<Interpreted> factory) throws IOException {
    return factory.of(this);
  }

  /**
   * A file, possibly absent. Nothing lies beneath a file. Its bytes are read
   * and written through its {@link Lease leases}.
   */
  sealed interface File extends Shape permits Node.File, Entry.File, Source.File {

    /**
     * Describes the file's bytes without reading them.
     *
     * @return what is known about the bytes, bound to this file
     * @throws Refusal     with {@link Reason.Place.Missing} when nothing is
     *                     here, or {@link Reason.Place.Occupied} when
     *                     something other than a file is here or above
     * @throws IOException when the file cannot be inspected
     */
    @NonNull
    Describe describe() throws IOException;

    /**
     * Leases this file for reading.
     *
     * @return open lease, owned by the caller
     * @throws Refusal     with {@link Reason.Place.Held} when the tree does
     *                     not wait and the file is leased for writing
     * @throws IOException when the lease cannot be taken
     */
    @NonNull
    Lease.Read read() throws IOException;

    /**
     * Leases this file for writing, and for reading what it writes. The file
     * may be absent: replacing it creates it.
     *
     * @return open lease, owned by the caller
     * @throws Refusal     with {@link Reason.Place.Held} when the tree does
     *                     not wait and the file is leased, or
     *                     {@link Reason.Place.Denied} when its tree is
     *                     read-only, as a source and an archive are
     * @throws IOException when the lease cannot be taken
     */
    @NonNull
    Lease.Write write() throws IOException;

    /**
     * Reads the whole file, for content small enough to hold in memory.
     *
     * @return the file's bytes
     * @throws IOException as {@link #read()} and {@link Lease.Read#bytes()}
     *                     throw
     */
    @NonNull
    default byte[] bytes() throws IOException {
      try (Lease.Read lease = read()) {
        return lease.bytes();
      }
    }
  }

  /**
   * A directory, possibly absent, holding the places beneath it.
   */
  sealed interface Directory extends Shape permits Node.Directory, Entry.Directory {

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
     * Finds every file of a name that exists beneath this directory, at any
     * depth, such as the manifests an archive holds.
     *
     * @param name single filename, matched exactly
     * @return the files found, each directory's before its subdirectories',
     *         possibly none
     * @throws IOException when a directory cannot be listed
     */
    @NonNull
    default List<Shape.File> find(@NonNull String name) throws IOException {
      List<Shape.File> found = new ArrayList<>();
      for (Shape child : children()) {
        switch (child) {
          case Shape.File file when file.name().equals(name) -> found.add(file);
          case Shape.File _ -> {
          }
          case Shape.Directory directory -> found.addAll(directory.find(name));
        }
      }
      return List.copyOf(found);
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
