package cat.tabbie.sdk.platform.layout;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * A place in the declared shape of a root, such as a Cat, at its own
 * {@link Address}: a declared directory or file, or content beneath a declared
 * directory that is not itself declared. Each has the {@link Rule rules} that
 * say what may happen to it. A declared directory holds its declared children,
 * so the directory at the root address is the whole tree. The distribution
 * build a Cat runs declares that tree, and it is kept with the Cat, so every
 * change made under it can name the tree it was made under.
 *
 * <p>
 * Nodes carry no meaning beyond their addresses and rules, so a tree never
 * needs a special kind of node for one game feature. Moving between nodes
 * never looks at a filesystem: {@link #locate(Address) locating} follows
 * declared names, and {@link Directory#find(Class) finding} lists the declared
 * directories that accept a type. A node's {@link #address()} leaves the tree
 * as a plain address, and locating that address from the root of the tree
 * finds the node again.
 */
public sealed interface Node permits Node.Directory, Node.File, Node.Beneath {

  /**
   * Returns where this node is.
   *
   * @return address beneath the root, the root address only for the directory
   *         standing for the root
   */
  @NonNull
  Address address();

  /**
   * Returns what may happen to this node, and beneath it for a directory.
   *
   * @return immutable rules, possibly empty
   */
  @NonNull
  Set<Rule> rules();

  /**
   * Returns this node's name, the last component of its address.
   *
   * @return single filename, empty only for the directory standing for the
   *         root
   */
  default String name() {
    return address().fileName().orElse("");
  }

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
   * Requires this node to hold a permission.
   *
   * @param permission permission the operation needs
   * @return this node
   * @throws Violation.Forbidden when it does not hold it
   */
  default Node require(@NonNull Rule.Permission permission) {
    if (!permits(permission)) {
      throw new Violation.Forbidden(address(), permission);
    }
    return this;
  }

  /**
   * Locates the node at names beneath this one, as {@link #locate(Address)}
   * does.
   *
   * @param names single-name components, none for this node itself
   * @return the node there
   * @throws Violation.Undeclared     when a step leads beneath a declared file
   * @throws IllegalArgumentException when a component is not a single portable
   *                                  filename
   */
  default Node locate(@NonNull String... names) {
    return locate(Address.of(names));
  }

  /**
   * Locates the node at an address relative to this one, one step per
   * component: onto a declared child, or onto content a directory does not
   * declare, which the directory governs. From the root, the address of any
   * node in the tree locates that node.
   *
   * @param tail address relative to this node
   * @return the node there, or this node for the root address
   * @throws Violation.Undeclared when a step leads beneath a declared file
   */
  default Node locate(@NonNull Address tail) {
    Node located = this;
    for (String name : tail.components()) {
      located = step(located, name);
    }
    return located;
  }

  /**
   * Takes one step down from a node.
   */
  private static Node step(Node node, String name) {
    return switch (node) {
      case Directory directory -> directory.children().stream()
          .filter(child -> child.name().equals(name))
          .findFirst()
          .orElseGet(() -> new Beneath(directory.address().resolve(name), directory));
      case File file -> throw new Violation.Undeclared(file.address(), name);
      case Beneath beneath -> new Beneath(beneath.address().resolve(name), beneath.governor());
    };
  }

  /**
   * A declared directory, holding declared children. Content it governs but
   * does not declare, such as the mods placed in a mods directory, may lie
   * beneath it as its rules allow. The directory at the root address stands
   * for the root itself.
   *
   * @param address  address of the directory, the root address for the root
   * @param rules    what may happen to it and to undeclared content beneath it
   * @param children declared directories and files, each directly beneath it
   *                 and uniquely named
   */
  record Directory(
      @NonNull Address address,
      @NonNull Set<Rule> rules,
      @NonNull List<Node> children) implements Node {

    /**
     * Copies the rules and children, and checks the children.
     *
     * @throws IllegalArgumentException when a child is undeclared content, does
     *                                  not lie directly beneath this directory,
     *                                  or shares a name with another
     */
    public Directory {
      rules = Set.copyOf(rules);
      children = List.copyOf(children);
      if (children.stream().anyMatch(Beneath.class::isInstance)) {
        throw new IllegalArgumentException("A directory declares only directories and files.");
      }
      Optional<Address> parent = Optional.of(address);
      if (children.stream().anyMatch(child -> !child.address().parent().equals(parent))) {
        throw new IllegalArgumentException("A declared child lies directly beneath its directory.");
      }
      if (children.stream().map(Node::name).distinct().count() != children.size()) {
        throw new IllegalArgumentException("A directory declares each name only once.");
      }
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

    /**
     * Finds the declared directories that accept content of a type, this one
     * included, in declaration order: a directory before its children, and
     * children in the order they are declared. Only declared directories are
     * found, so nothing the tree does not declare is searched.
     *
     * @param type type of the content to place
     * @return the accepting directories, possibly none
     */
    public Accepting find(@NonNull Class<?> type) {
      return new Accepting(type, accepting(type).toList());
    }

    /**
     * Lists this directory and its declared descendant directories that accept
     * a type, in declaration order.
     */
    private Stream<Directory> accepting(Class<?> type) {
      return Stream.concat(
          Stream.of(this).filter(_ -> accepts(type)),
          children.stream()
              .filter(Directory.class::isInstance)
              .map(Directory.class::cast)
              .flatMap(directory -> directory.accepting(type)));
    }
  }

  /**
   * A declared file. A file holds no content of other kinds, so it accepts
   * nothing, and nothing lies beneath it.
   *
   * @param address address of the file
   * @param rules   what may happen to it
   */
  record File(@NonNull Address address, @NonNull Set<Rule> rules) implements Node {

    /**
     * Copies the rules, and checks the address and that no rule accepts
     * content.
     *
     * @throws IllegalArgumentException when the address is the root, or a rule
     *                                  accepts content
     */
    public File {
      rules = Set.copyOf(rules);
      if (address.isRoot()) {
        throw new IllegalArgumentException("Only a directory stands for the root.");
      }
      if (rules.stream().anyMatch(Rule.Accepts.class::isInstance)) {
        throw new IllegalArgumentException("A file accepts no content.");
      }
    }
  }

  /**
   * Content the tree does not declare, beneath a declared directory, such as
   * {@code mods/sodium.jar}. It may be a file or a directory. The directory
   * governs it, so it has the directory's rules.
   *
   * @param address  address of the content
   * @param governor nearest declared directory above it
   */
  record Beneath(@NonNull Address address, @NonNull Directory governor) implements Node {

    /**
     * Checks that the governor is the nearest declared directory above the
     * address.
     *
     * @throws IllegalArgumentException when the address does not lie beneath
     *                                  the governor, or the governor declares
     *                                  the first step towards it
     */
    public Beneath {
      if (!address.within(governor.address())) {
        throw new IllegalArgumentException("Undeclared content lies beneath the directory that governs it.");
      }
      String step = address.components().get(governor.address().components().size());
      if (governor.children().stream().anyMatch(child -> child.name().equals(step))) {
        throw new IllegalArgumentException("A directory governs only content it does not declare.");
      }
    }

    /**
     * Returns the rules of the directory that governs this content.
     *
     * @return the governor's rules
     */
    @Override
    public Set<Rule> rules() {
      return governor.rules();
    }
  }

  /**
   * The declared directories that accept one type of content, as
   * {@link Directory#find(Class)} found them. Several directories may accept
   * the same type: work placing the content picks among them, knowing the
   * distribution it installs for, or requires {@link #unique() exactly one}.
   *
   * @param type        type of the content to place
   * @param directories accepting directories, in declaration order, possibly
   *                    none
   */
  record Accepting(@NonNull Class<?> type, @NonNull List<Directory> directories) {

    /**
     * Copies the directories, and checks that each accepts the type and is
     * listed once.
     *
     * @throws Violation.Unaccepted     when a directory does not accept the
     *                                  type
     * @throws IllegalArgumentException when a directory is listed twice
     */
    public Accepting {
      directories = List.copyOf(directories);
      for (Directory directory : directories) {
        if (!directory.accepts(type)) {
          throw new Violation.Unaccepted(directory.address(), type);
        }
      }
      if (directories.stream().map(Directory::address).distinct().count() != directories.size()) {
        throw new IllegalArgumentException("An accepting directory is listed only once.");
      }
    }

    /**
     * Requires exactly one accepting directory, for work that places content
     * the same way wherever the tree puts it.
     *
     * @return the only accepting directory
     * @throws Violation.Missing   when no directory accepts the type
     * @throws Violation.Ambiguous when several do
     */
    public Directory unique() {
      if (directories.isEmpty()) {
        throw new Violation.Missing(type);
      }
      if (directories.size() > 1) {
        throw new Violation.Ambiguous(type, directories.stream().map(Directory::address).toList());
      }
      return directories.getFirst();
    }
  }
}
