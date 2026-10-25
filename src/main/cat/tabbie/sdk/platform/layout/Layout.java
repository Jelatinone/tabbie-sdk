package cat.tabbie.sdk.platform.layout;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * The declared shape of a root, such as a Cat: a tree of {@link Node nodes} for
 * the root, and one for the inside of a World, each node with the {@link Rule rules} that say
 * what may happen there. A layout is immutable data. The distribution build a
 * Cat runs declares it, and it is kept with the Cat, so every change made under
 * it can name the layout it was made under.
 *
 * <p>
 * Finding an address in a layout never looks at a filesystem: it follows the
 * declared names, landing on a declared node or on undeclared content beneath
 * a declared directory. A World's directory is named by whoever runs the work,
 * so finding inside a World takes that directory's address.
 *
 * @param root  unnamed directory standing for the root itself
 * @param world unnamed directory standing for the inside of a World
 */
public record Layout(@NonNull Node.Directory root, @NonNull Node.Directory world) {

  /**
   * Checks that the directories standing for the root and a World are unnamed.
   *
   * @throws IllegalArgumentException when either has a name
   */
  public Layout {
    if (!root.name().isEmpty() || !world.name().isEmpty()) {
      throw new IllegalArgumentException("A directory standing for the root or a World has no name.");
    }
  }

  /**
   * Finds where an address lands in the root's tree.
   *
   * @param address address beneath the root
   * @return the declared node it names, or the declared directory it lies
   *         beneath
   * @throws Violation.Undeclared when the address leads beneath a declared
   *                              file
   */
  public Located find(@NonNull Address address) {
    return walk(new Located.Declared(Address.root(), root), address, Address.root());
  }

  /**
   * Finds where an address lands in a World's tree.
   *
   * @param address address in the World
   * @param world   address of the World's directory
   * @return the declared node it names, or the declared directory it lies
   *         beneath
   * @throws Address.External     when the address does not lie in the World
   * @throws Violation.Undeclared when the address leads beneath a declared
   *                              file
   */
  public Located find(@NonNull Address address, @NonNull Address world) {
    return walk(new Located.Declared(world, this.world), address.require(world), world);
  }

  /**
   * Follows an address's components beneath a directory, from where that
   * directory landed.
   */
  private static Located walk(Located start, Address address, Address directory) {
    Located located = start;
    for (String name : address.components().subList(directory.components().size(), address.components().size())) {
      located = located.resolve(name);
    }
    return located;
  }
}
