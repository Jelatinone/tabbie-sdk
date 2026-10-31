package cat.tabbie.sdk.platform.layout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import cat.tabbie.sdk.album.revision.Chunk;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Malformation;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Reference;
import lombok.NonNull;

/**
 * What really exists beneath a root on a plate: a file or a directory at its
 * own {@link Address}. A place where nothing exists has no node. Nodes hold
 * no rules; the plate checks the model it was given when it carries a change
 * out.
 *
 * <p>
 * A {@link cat.tabbie.sdk.album.model.Plate Plate} implements the nodes of its
 * tree as views of its own files, so a change carried out on the plate shows
 * in its tree at once, and the same work sees a rehearsal's overlay or the Cat
 * itself depending only on the plate it runs on. Changes to what exists are
 * described from nodes as images, and carried out only through the
 * {@link Lease leases} nodes grant. A plate implements only each node's
 * {@link #address()}, {@link #parent()} and {@link #path()}, a directory's
 * {@link Directory#children() children} and
 * {@link Directory#claim(Address) claims}, and a file's leases; everything else
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

  @Override
  @NonNull
  default Node locate(@NonNull Address tail) {
    return (Node) Shape.super.locate(tail);
  }

  @Override
  @NonNull
  default Node locate(@NonNull String... names) {
    return locate(Address.of(names));
  }

  /**
   * A file that exists. It is read and written through its own leases, and
   * describes changes to itself as images, which are carried out only when
   * they are exposed.
   */
  non-sealed interface File extends Node, Shape.File {

    /**
     * Leases this file for reading.
     *
     * @return open lease, owned by the caller
     * @throws Refusal     with {@link Refusal.Held} when the plate does not
     *                     wait and the file is leased for writing
     * @throws IOException when the lease cannot be taken
     */
    @NonNull
    Lease.Read read() throws IOException;

    /**
     * Leases this file for writing.
     *
     * @return open lease, owned by the caller
     * @throws Refusal     with {@link Refusal.Held} when the plate does not
     *                     wait and the file is leased
     * @throws IOException when the lease cannot be taken
     */
    @NonNull
    Lease.Write write() throws IOException;

    /**
     * Leases this file for reading and writing, for work that checks a file
     * before it changes it.
     *
     * @return open lease, owned by the caller
     * @throws Refusal     with {@link Refusal.Held} when the plate does not
     *                     wait and the file is leased
     * @throws IOException when the lease cannot be taken
     */
    @NonNull
    Lease.Mutual mutual() throws IOException;

    /**
     * Describes replacing this file's whole content.
     *
     * @param before retained content before
     * @param after  retained content after
     * @return image replacing the file
     * @throws Malformation.Unchanged when both contents are the same
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
     * @throws Malformation.Unchanged when no fragment changes
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
     * @throws Malformation.Unchanged when no chunk edits its text
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
     * @throws Malformation.Unchanged when the texts are equal
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
     * @throws Malformation.Unchanged when the texts are equal
     */
    @NonNull
    default Image<Image.Text> configure(@NonNull String before, @NonNull String after, int contextLines) {
      return configure(Chunk.diff(before, after, contextLines));
    }
  }

  /**
   * A directory that exists, holding the nodes that exist directly beneath
   * it.
   */
  non-sealed interface Directory extends Node, Shape.Directory {

    /**
     * Lists the nodes that exist directly beneath this directory. A plate
     * lists them the first time something goes beneath the directory, and
     * keeps the listing current as its own leases change it.
     *
     * @return uniquely named nodes, in the order the tree holds them, possibly
     *         empty
     */
    @Override
    @NonNull
    List<Node> children();

    @Override
    @NonNull
    default Optional<Node> child(@NonNull String name) {
      return children().stream()
          .filter(child -> child.name().equals(name))
          .findFirst();
    }

    /**
     * Requires nothing to exist at a tail beneath this directory, where a
     * file is to be created.
     *
     * @param tail address of the file relative to this directory
     * @return this directory
     * @throws Violation.Occupied       when something exists at the tail
     * @throws Violation.Terminal       when a file lies on the way
     * @throws IllegalArgumentException when the tail names this directory
     */
    @Override
    @NonNull
    default Node.Directory require(@NonNull Address tail) {
      if (tail.isRoot()) {
        throw new IllegalArgumentException("A file is created beneath a directory, not in its place.");
      }
      try {
        locate(tail);
      } catch (Violation.Missing missing) {
        return this;
      }
      throw new Violation.Occupied(address().resolve(tail));
    }

    /**
     * Leases the place at a tail beneath this directory for creating a file
     * there. The plate refuses while anything exists there, as
     * {@link #require(Address)} would, and checks under the lease, so no
     * other work can create something there first.
     *
     * @param tail address of the file relative to this directory
     * @return open lease, owned by the caller
     * @throws Violation                when something exists at the tail or a
     *                                  file lies on the way
     * @throws Refusal                  with {@link Refusal.Held} when the plate
     *                                  does not wait and the place is leased
     * @throws IOException              when the lease cannot be taken
     * @throws IllegalArgumentException when the tail names this directory
     */
    @NonNull
    Lease.Create claim(@NonNull Address tail) throws IOException;
  }
}
