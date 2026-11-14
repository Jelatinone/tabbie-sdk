package cat.tabbie.sdk.tree;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.revision.Hunk;
import cat.tabbie.sdk.revision.Image;
import cat.tabbie.sdk.revision.Revision;
import lombok.NonNull;

/**
 * A place beneath a root on a plate: a file or a directory at its own
 * {@link Address}, whether or not anything exists there. Nodes hold no rules;
 * the plate checks the model it was given when it carries a change out.
 *
 * <p>
 * A {@link cat.tabbie.sdk.revision.Plate Plate} implements the nodes of its
 * tree as views of its own files, so a change carried out on the plate shows
 * in its tree at once, and the same work sees a rehearsal's overlay or the Cat
 * itself depending only on the plate it runs on. Changes are described from
 * nodes as images, and carried out only through the {@link Lease leases}
 * nodes grant. A plate implements only each node's {@link #address()},
 * {@link #parent()} and {@link #state()}, a directory's
 * {@link Directory#children() children} and the places it
 * {@link Directory#file(Address) names}, and a file's
 * {@link Shape.File#describe() description} and leases; everything else here
 * follows from those. Plates' nodes are safe for concurrent use.
 */
public sealed interface Node extends Shape permits Node.File, Node.Directory {

  @Override
  @NonNull
  Optional<Node.Directory> parent();

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
     * Describes changing this file by a revision.
     *
     * @param <Side>   what each side of the revision is
     * @param revision how its content changes
     * @return image configuring the file
     */
    @NonNull
    default <Side extends Image.Alteration> Image<Side> configure(@NonNull Revision<Side> revision) {
      return new Image.Configuration<>(address(), revision);
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
      return configure(before, after, 3);
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
      if (before.equals(after)) {
        throw new Violation(new Reason.Change.Unchanged(address()));
      }
      return configure(new Revision.Diff(Hunk.diff(before, after, contextLines)));
    }

    /**
     * Describes replacing this file's whole content with another file's. The
     * file's bytes are read now and held in memory, so the image's preimage
     * restores them whatever happens to the file meanwhile.
     *
     * @param after the file whose bytes this file takes
     * @return image swapping the content
     * @throws Violation   with {@link Reason.Change.Unchanged} when both hold
     *                     the same bytes
     * @throws Refusal     as reading this file refuses, such as when nothing
     *                     is here
     * @throws IOException when either file cannot be read
     */
    @NonNull
    default Image<Image.File.Present> replace(@NonNull Shape.File after) throws IOException {
      Image.File.Present before = new Image.File.Present(Source.of(name(), bytes()).describe());
      Image.File.Present replaced = new Image.File.Present(after.describe());
      if (before.same(replaced)) {
        throw new Violation(new Reason.Change.Unchanged(address()));
      }
      return configure(new Revision.Swap(before, replaced));
    }

    /**
     * Describes deleting this file. Its bytes are read now and held in memory,
     * so the image's preimage restores them.
     *
     * @return image deleting the file
     * @throws Refusal     as reading this file refuses, such as when nothing
     *                     is here
     * @throws IOException when the file cannot be read
     */
    @NonNull
    default Image<Image.File> delete() throws IOException {
      return new Image.Installation(new Image.File.Present(Source.of(name(), bytes()).describe()),
          new Image.File.Absent(), address());
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
     * Describes copying a shape into this directory, from any tree: a file
     * lands under its own name, and a directory's contents land beneath this
     * one, merging with what is described here. Each file's image is made from
     * its {@link Shape.File#describe() description}, and its bytes move only
     * when the image is exposed.
     *
     * @param shape the file or directory to copy
     * @return images creating each file, one per file
     * @throws Refusal     as describing or listing the shape refuses
     * @throws IOException when the shape cannot be read
     */
    @NonNull
    default Set<Image<?>> receive(@NonNull Shape shape) throws IOException {
      return receive(List.of(shape));
    }

    /**
     * Describes copying a shape into this directory under another name: a file
     * lands under that name, and a directory's contents land beneath the
     * directory of that name.
     *
     * @param shape the file or directory to copy
     * @param name  single filename it takes here
     * @return images creating each file, one per file
     * @throws Refusal                  as describing or listing the shape
     *                                  refuses
     * @throws IOException              when the shape cannot be read
     * @throws IllegalArgumentException when the name is not a portable single
     *                                  filename
     */
    @NonNull
    default Set<Image<?>> receive(@NonNull Shape shape, @NonNull String name) throws IOException {
      return switch (shape) {
        case Shape.File file -> Set.of(new Image.Installation(new Image.File.Absent(),
            new Image.File.Present(file.describe()), address().resolve(name)));
        case Shape.Directory directory -> directory(name).receive(directory);
      };
    }

    /**
     * Describes copying layers into this directory, each as
     * {@link #receive(Shape)} does, where a later layer's file replaces an
     * earlier layer's at the same place.
     *
     * @param layers files and directories to copy, earliest first
     * @return images creating each file, one per place
     * @throws Refusal     as describing or listing a layer refuses
     * @throws IOException when a layer cannot be read
     */
    @NonNull
    default Set<Image<?>> receive(@NonNull List<? extends Shape> layers) throws IOException {
      Map<Address, Image<?>> placed = new LinkedHashMap<>();
      for (Shape layer : layers) {
        Set<Image<?>> images = switch (layer) {
          case Shape.File file -> receive(file, file.name());
          case Shape.Directory directory -> {
            Map<Address, Image<?>> beneath = new LinkedHashMap<>();
            for (Shape child : directory.children()) {
              receive(child, child.name()).forEach(image -> beneath.put(image.address(), image));
            }
            yield Set.copyOf(beneath.values());
          }
        };
        images.forEach(image -> placed.put(image.address(), image));
      }
      return Set.copyOf(placed.values());
    }
  }
}
