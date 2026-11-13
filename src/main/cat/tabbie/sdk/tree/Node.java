package cat.tabbie.sdk.tree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.revision.Chunk;
import cat.tabbie.sdk.revision.Image;
import lombok.NonNull;

/**
 * A place beneath a root on a plate: a file or a directory at its own
 * {@link Address}, whether or not anything exists there. Nodes hold no rules;
 * the plate checks the model it was given when it carries a change out.
 *
 * <p>
 * A {@link cat.tabbie.sdk.plate.Plate Plate} implements the nodes of its
 * tree as views of its own files, so a change carried out on the plate shows
 * in its tree at once, and the same work sees a rehearsal's overlay or the Cat
 * itself depending only on the plate it runs on. Changes to what exists are
 * described from nodes as images, and carried out only through the
 * {@link Lease leases} nodes grant. A plate implements only each node's
 * {@link #address()}, {@link #parent()}, {@link #path()} and {@link #state()},
 * a directory's {@link Directory#children() children} and the places it
 * {@link Directory#file(Address) names}, and a file's leases; everything else
 * here follows from those. Plates' nodes are safe for concurrent use.
 */
public sealed interface Node extends Shape permits Node.File, Node.Directory {

  @Override
  @NonNull
  Optional<Node.Directory> parent();

  /**
   * Returns this node's physical path, only for handing it to something
   * outside the tree, such as a command an outside process runs or a ZIP
   * reader. Work reads and writes files through leases, never through this
   * path: while rehearsing, it names the real file rather than the overlay.
   *
   * @return physical path
   */
  @NonNull
  Path path();

  /**
   * Returns the directory standing for the root of this node's tree, by
   * following {@link #parent()} up as far as it goes.
   *
   * @return root directory, this node itself when it stands for the root
   * @throws IllegalStateException when this is a file beneath no directory
   */
  @NonNull
  default Node.Directory root() {
    Node.Directory reached = switch (this) {
      case Node.Directory directory -> directory;
      case Node.File file -> file.parent()
          .orElseThrow(() -> new IllegalStateException("A file lies beneath a directory."));
    };
    while (reached.parent().isPresent()) {
      reached = reached.parent().get();
    }
    return reached;
  }

  /**
   * A file, possibly absent. It is read and written through its own leases,
   * and describes changes to itself as images, which are carried out only
   * when they are exposed.
   */
  non-sealed interface File extends Node, Shape.File {

    /**
     * Leases this file for reading.
     *
     * @return open lease, owned by the caller
     * @throws Refusal     with {@link Reason.Place.Held} when the plate does
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
     * @throws Refusal     with {@link Reason.Place.Held} when the plate does
     *                     not wait and the file is leased
     * @throws IOException when the lease cannot be taken
     */
    @NonNull
    Lease.Write write() throws IOException;

    /**
     * Describes replacing this file's whole content.
     *
     * @param before retained content before
     * @param after  retained content after
     * @return image replacing the file
     * @throws Violation with {@link Reason.Change.Unchanged} when both contents
     *                   are the same
     */
    @NonNull
    default Image<Image.File> replace(@NonNull Reference.Captured before, @NonNull Reference.Captured after) {
      return new Image.Installation(new Image.File.Present(before), new Image.File.Present(after), address());
    }

    /**
     * Describes deleting this file, whose complete content matches the
     * reference.
     *
     * @param before retained content before
     * @return image deleting the file
     */
    @NonNull
    default Image<Image.File> delete(@NonNull Reference.Captured before) {
      return new Image.Installation(new Image.File.Present(before), new Image.File.Absent(), address());
    }

    /**
     * Describes changes to this file's text between explicit fragment states,
     * which pair up into chunks.
     *
     * @param before text before
     * @param after  text after
     * @return image configuring the file
     * @throws Violation with {@link Reason.Change.Unchanged} when no fragment
     *                   changes
     */
    @NonNull
    default Image<Image.Text> configure(@NonNull Image.Text before, @NonNull Image.Text after) {
      return new Image.Configuration(before, after, address());
    }

    /**
     * Describes changes to this file's text, each chunk contributing a
     * fragment on each side.
     *
     * @param chunks changed chunks
     * @return image configuring the file
     * @throws Violation with {@link Reason.Change.Unchanged} when no chunk
     *                   edits its text
     */
    @NonNull
    default Image<Image.Text> configure(@NonNull List<Chunk> chunks) {
      return new Image.Configuration(chunks, address());
    }

    /**
     * Describes the differences between two texts of this file, with three
     * surrounding context lines.
     *
     * @param before text before
     * @param after  text after
     * @return image configuring the file
     * @throws Violation with {@link Reason.Change.Unchanged} when the texts are
     *                   equal
     */
    @NonNull
    default Image<Image.Text> configure(@NonNull String before, @NonNull String after) {
      return configure(Chunk.diff(before, after));
    }

    /**
     * Describes the differences between two texts of this file, keeping only
     * the differing regions and their context.
     *
     * @param before       text before
     * @param after        text after
     * @param contextLines number of context lines to keep
     * @return image configuring the file
     * @throws Violation with {@link Reason.Change.Unchanged} when the texts are
     *                   equal
     */
    @NonNull
    default Image<Image.Text> configure(@NonNull String before, @NonNull String after, int contextLines) {
      return configure(Chunk.diff(before, after, contextLines));
    }
  }

  /**
   * A directory, possibly absent, holding the nodes that exist directly
   * beneath it.
   */
  non-sealed interface Directory extends Node, Shape.Directory {

    /**
     * Lists the nodes that exist directly beneath this directory. A plate
     * lists them the first time something goes beneath the directory, and
     * keeps the listing current as its own leases change it.
     *
     * @return uniquely named nodes, in the order the tree holds them, empty
     *         when this directory is absent
     * @throws IOException when the directory cannot be listed
     */
    @Override
    @NonNull
    List<Node> children() throws IOException;

    @Override
    @NonNull
    default Optional<Node> child(@NonNull String name) throws IOException {
      return children().stream()
          .filter(child -> child.name().equals(name))
          .findFirst();
    }

    @Override
    @NonNull
    Node.File file(@NonNull Address tail);

    @Override
    @NonNull
    default Node.File file(@NonNull String... names) {
      return file(Address.of(names));
    }

    @Override
    @NonNull
    Node.Directory directory(@NonNull Address tail);

    @Override
    @NonNull
    default Node.Directory directory(@NonNull String... names) {
      return directory(Address.of(names));
    }

    /**
     * Requires nothing to exist at a tail beneath this directory, where a
     * file is to be created, and only directories on the way to it.
     *
     * @param tail address of the file relative to this directory
     * @return this directory
     * @throws Violation                with {@link Reason.Place.Occupied} when
     *                                  something exists at the tail, or
     *                                  something other than a directory lies on
     *                                  the way
     * @throws IOException              when a place on the way cannot be
     *                                  inspected
     * @throws IllegalArgumentException when the tail names this directory
     */
    @NonNull
    default Node.Directory require(@NonNull Address tail) throws IOException {
      if (tail.isRoot()) {
        throw new IllegalArgumentException("A file is created beneath a directory, not in its place.");
      }
      List<String> names = tail.components();
      for (int depth = 1; depth <= names.size(); depth++) {
        Address step = Address.root().resolve(names.subList(0, depth));
        State found = directory(step).state();
        if (found instanceof State.Absent) {
          return this;
        }
        if (depth == names.size() || !(found instanceof State.Directory)) {
          throw new Violation(new Reason.Place.Occupied(address().resolve(tail), address().resolve(step), found));
        }
      }
      return this;
    }

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
     * @throws IOException              when a place on the way cannot be
     *                                  inspected
     * @throws IllegalArgumentException when the tail names this directory
     */
    @NonNull
    default Image<Image.File> create(@NonNull Address tail, @NonNull Reference.Captured after) throws IOException {
      require(tail);
      return new Image.Installation(new Image.File.Absent(), new Image.File.Present(after), address().resolve(tail));
    }
  }
}
