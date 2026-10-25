package cat.tabbie.sdk.platform.layout;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * Where an {@link Address} lands in a {@link Layout}: on a declared node, or
 * beneath a declared directory on content the layout does not declare, such as
 * {@code mods/sodium.jar}. Either way, one declared node's rules govern it.
 */
public sealed interface Located permits Located.Declared, Located.Beneath {

  /**
   * Returns the address that landed here.
   *
   * @return the address
   */
  @NonNull
  Address address();

  /**
   * Returns the declared node whose rules govern the address.
   *
   * @return governing node
   */
  @NonNull
  Node node();

  /**
   * Takes one more step down: onto the declared file or directory of a name,
   * or beneath the directory onto content the layout does not declare.
   *
   * @param name single filename
   * @return where the step landed
   * @throws Violation.Undeclared     when the step leads beneath a declared
   *                                  file
   * @throws IllegalArgumentException when the name is not a single filename
   */
  default Located resolve(@NonNull String name) {
    return switch (this) {
      case Declared(Address address, Node.Directory directory) -> directory.find(name)
          .<Located>map(child -> new Declared(address.resolve(name), child))
          .orElseGet(() -> new Beneath(address.resolve(name), directory));
      case Declared(Address address, Node.File _) -> throw new Violation.Undeclared(address, name);
      case Beneath(Address address, Node.Directory directory) -> new Beneath(address.resolve(name), directory);
    };
  }

  /**
   * Requires the governing node to hold a permission.
   *
   * @param permission permission the operation needs
   * @return this location
   * @throws Violation.Forbidden when the node does not hold it
   */
  default Located require(@NonNull Rule.Permission permission) {
    if (!node().permits(permission)) {
      throw new Violation.Forbidden(address(), permission);
    }
    return this;
  }

  /**
   * Requires the governing node to be a directory accepting content of a type.
   *
   * @param type type of the content to place
   * @return this location
   * @throws Violation.Unaccepted when the node is a file, or does not accept
   *                              the type
   */
  default Located accept(@NonNull Class<?> type) {
    if (!(node() instanceof Node.Directory directory && directory.accepts(type))) {
      throw new Violation.Unaccepted(address(), type);
    }
    return this;
  }

  /**
   * A declared node.
   *
   * @param address address of the node
   * @param node    the node, which governs itself
   */
  record Declared(@NonNull Address address, @NonNull Node node) implements Located {
  }

  /**
   * Content the layout does not declare, beneath a declared directory.
   *
   * @param address address of the content
   * @param node    nearest declared directory above it, which governs it
   */
  record Beneath(@NonNull Address address, @NonNull Node.Directory node) implements Located {
  }
}
