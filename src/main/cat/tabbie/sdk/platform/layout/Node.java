package cat.tabbie.sdk.platform.layout;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * One declared entry of a {@link Layout}: a directory or a file, by name, with
 * the {@link Rule rules} that say what may happen to it. Nodes carry no meaning
 * beyond their names and rules, so a layout never needs a special kind of node
 * for one game feature.
 */
public sealed interface Node permits Node.Directory, Node.File {

  /**
   * Returns this node's name.
   *
   * @return single filename, empty only for a directory standing for a root or
   *         a World
   */
  @NonNull
  String name();

  /**
   * Returns what may happen to this node, and beneath it for a directory.
   *
   * @return immutable rules, possibly empty
   */
  @NonNull
  Set<Rule> rules();

  /**
   * Whether this node holds a permission.
   *
   * @param permission permission to check
   * @return true when held
   */
  default boolean permits(@NonNull Rule.Permission permission) {
    return rules().contains(permission);
  }

  /**
   * A declared directory, holding declared children. Content it governs but
   * does not declare, such as the mods placed in a mods directory, may lie
   * beneath it as its rules allow.
   *
   * @param name     single filename, empty only when it stands for a root or a
   *                 World
   * @param rules    what may happen to it and to undeclared content beneath it
   * @param children declared children, uniquely named
   */
  record Directory(
      @NonNull String name,
      @NonNull Set<Rule> rules,
      @NonNull List<Node> children) implements Node {

    /**
     * Copies the rules and children, and checks the names.
     *
     * @throws IllegalArgumentException when a name is not a single filename, a
     *                                  child is unnamed, or two children share
     *                                  a name
     */
    public Directory {
      rules = Set.copyOf(rules);
      children = List.copyOf(children);
      if (!name.isEmpty()) {
        Address.validateFilename(name);
      }
      if (children.stream().map(Node::name).anyMatch(String::isEmpty)) {
        throw new IllegalArgumentException("Only a directory standing for a root or a World may be unnamed.");
      }
      if (children.stream().map(Node::name).distinct().count() != children.size()) {
        throw new IllegalArgumentException("A directory declares each name only once.");
      }
    }

    /**
     * Finds the declared file or directory of a name.
     *
     * @param name declared name
     * @return the child, or empty when none is declared
     */
    public Optional<Node> find(@NonNull String name) {
      return children.stream()
          .filter(child -> child.name().equals(name))
          .findFirst();
    }

    /**
     * Whether this directory accepts content of a type.
     *
     * @param type type of the content to place
     * @return true when one of its {@link Rule.Accepts} rules accepts the type
     */
    public boolean accepts(@NonNull Class<?> type) {
      return rules.stream()
          .anyMatch(rule -> rule instanceof Rule.Accepts accepts && accepts.accepts(type));
    }
  }

  /**
   * A declared file. A file holds no content of other kinds, so it accepts
   * nothing.
   *
   * @param name  single filename
   * @param rules what may happen to it
   */
  record File(@NonNull String name, @NonNull Set<Rule> rules) implements Node {

    /**
     * Copies the rules, and checks the name and that no rule accepts content.
     *
     * @throws IllegalArgumentException when the name is not a single filename,
     *                                  or a rule accepts content
     */
    public File {
      rules = Set.copyOf(rules);
      Address.validateFilename(name);
      if (rules.stream().anyMatch(Rule.Accepts.class::isInstance)) {
        throw new IllegalArgumentException("A file accepts no content.");
      }
    }
  }
}
