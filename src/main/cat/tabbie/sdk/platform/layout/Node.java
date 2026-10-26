package cat.tabbie.sdk.platform.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * A place in the shape of a root, such as a Cat: a directory or a file at its
 * own {@link Address}, with the {@link Rule rules} that say what may happen to
 * it. A directory holds its children, so the directory at the root address is
 * the whole tree.
 *
 * <p>
 * A tree is either a declaration or a plate's live tree. A declaration says
 * what goes where and what may happen there: the distribution build a Cat runs
 * declares one, and core may declare more. A
 * {@link cat.tabbie.sdk.album.model.Plate Plate} keeps a live tree: the
 * declaration with every file that exists beneath the plate's root
 * {@link Directory#add(Address) added}, kept current as work creates and
 * deletes files, so work running on the plate sees what earlier work did.
 *
 * <p>
 * Nodes carry no meaning beyond their addresses and rules, so a tree never
 * needs a special kind of node for one game feature. Moving between nodes
 * never looks at a filesystem: {@link #locate(Address) locating} follows the
 * tree's names, and {@link Directory#find(Class) finding} lists the directories
 * that accept a type. A node's {@link #address()} leaves the tree as a plain
 * address, and locating that address from the root finds the node again.
 */
public sealed interface Node permits Node.Directory, Node.File {

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
   * @throws Violation.Missing        when the tree holds nothing at a step
   * @throws Violation.Terminal       when a step leads beneath a file
   * @throws IllegalArgumentException when a component is not a single portable
   *                                  filename
   */
  default Node locate(@NonNull String... names) {
    return locate(Address.of(names));
  }

  /**
   * Locates the node at an address relative to this one, following the tree's
   * names one component at a time.
   *
   * @param tail address relative to this node
   * @return the node there, or this node for the root address
   * @throws Violation.Missing  when the tree holds nothing at a step
   * @throws Violation.Terminal when a step leads beneath a file
   */
  default Node locate(@NonNull Address tail) {
    Node nearest = nearest(tail);
    int reached = nearest.address().components().size() - address().components().size();
    if (reached < tail.components().size()) {
      throw new Violation.Missing(nearest.address().resolve(tail.components().get(reached)));
    }
    return nearest;
  }

  /**
   * Finds the deepest node the tree holds along an address relative to this
   * one: the node at the address itself when the tree holds it, and otherwise
   * the last directory on the way that it does. Work describing a file that
   * does not exist yet checks this node's rules, since the directories created
   * above the file take their permissions from it.
   *
   * @param tail address relative to this node
   * @return deepest node along the address, or this node for the root address
   * @throws Violation.Terminal when a step leads beneath a file
   */
  default Node nearest(@NonNull Address tail) {
    Node nearest = this;
    for (String name : tail.components()) {
      Optional<Node> next = switch (nearest) {
        case Directory directory -> directory.child(name);
        case File file -> throw new Violation.Terminal(file.address(), name);
      };
      if (next.isEmpty()) {
        return nearest;
      }
      nearest = next.get();
    }
    return nearest;
  }

  /**
   * A directory, holding its children. The directory at the root address
   * stands for the root itself.
   *
   * @param address  address of the directory, the root address for the root
   * @param rules    what may happen to it and beneath it
   * @param children directories and files, each directly beneath it and
   *                 uniquely named
   */
  record Directory(
      @NonNull Address address,
      @NonNull Set<Rule> rules,
      @NonNull List<Node> children) implements Node {

    /**
     * Copies the rules and children, and checks the children.
     *
     * @throws IllegalArgumentException when a child does not lie directly
     *                                  beneath this directory, or shares a name
     *                                  with another
     */
    public Directory {
      rules = Set.copyOf(rules);
      children = List.copyOf(children);
      Optional<Address> parent = Optional.of(address);
      if (children.stream().anyMatch(child -> !child.address().parent().equals(parent))) {
        throw new IllegalArgumentException("A child lies directly beneath its directory.");
      }
      if (children.stream().map(Node::name).distinct().count() != children.size()) {
        throw new IllegalArgumentException("A directory holds each name only once.");
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
     * Finds the directories that accept content of a type, this one included,
     * in tree order: a directory before its children, and children in the
     * order they are held. Only declared directories accept content, since
     * directories a plate adds never take what their parent accepts.
     *
     * @param type type of the content to place
     * @return the accepting directories, possibly none
     */
    public Accepting find(@NonNull Class<?> type) {
      return new Accepting(type, accepting(type).toList());
    }

    /**
     * Adds a file that exists beneath this directory, as a plate does when
     * work creates one or when the plate finds one as it opens. Directories
     * missing above the file are added too. Each added node takes the
     * permissions of the directory above it, never what that directory
     * accepts.
     *
     * @param tail address of the file relative to this directory
     * @return this tree with the file added, or this tree when a file is
     *         already there
     * @throws Violation.Terminal       when the file would lie beneath a file
     * @throws IllegalArgumentException when the address names this directory,
     *                                  or a directory is already there
     */
    public Directory add(@NonNull Address tail) {
      if (tail.isRoot()) {
        throw new IllegalArgumentException("A file is added beneath a directory, not in its place.");
      }
      String name = tail.components().getFirst();
      Address rest = new Address(tail.components().subList(1, tail.components().size()));
      Optional<Node> existing = child(name);
      if (rest.isRoot()) {
        if (existing.isEmpty()) {
          return with(new File(address.resolve(name), inherited()));
        }
        if (existing.get() instanceof Directory) {
          throw new IllegalArgumentException("A directory is already where the file would be added.");
        }
        return this;
      }
      Node next = existing.orElseGet(() -> new Directory(address.resolve(name), inherited(), List.of()));
      Directory below = switch (next) {
        case Directory directory -> directory;
        case File file -> throw new Violation.Terminal(file.address(), rest.components().getFirst());
      };
      Directory grown = below.add(rest);
      return grown == below ? this : with(grown);
    }

    /**
     * Finds the child of a name.
     */
    private Optional<Node> child(String name) {
      return children.stream()
          .filter(child -> child.name().equals(name))
          .findFirst();
    }

    /**
     * Returns this directory with a child put in place of the one of its name,
     * or after the others when it has none.
     */
    private Directory with(Node child) {
      List<Node> changed = new ArrayList<>(children);
      int index = children.stream().map(Node::name).toList().indexOf(child.name());
      if (index < 0) {
        changed.add(child);
      } else {
        changed.set(index, child);
      }
      return new Directory(address, rules, changed);
    }

    /**
     * Returns the rules a node added beneath this directory takes: its
     * permissions, without what it accepts.
     */
    private Set<Rule> inherited() {
      return rules.stream()
          .filter(Rule.Permission.class::isInstance)
          .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Lists this directory and its descendant directories that accept a type,
     * in tree order.
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
   * A file. A file holds no content of other kinds, so it accepts nothing, and
   * nothing lies beneath it.
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
   * The directories that accept one type of content, as
   * {@link Directory#find(Class)} found them. Several directories may accept
   * the same type: work placing the content picks among them, knowing the
   * distribution it installs for, or requires {@link #unique() exactly one}.
   *
   * @param type        type of the content to place
   * @param directories accepting directories, in tree order, possibly none
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
     * @throws Violation.Unplaced  when no directory accepts the type
     * @throws Violation.Ambiguous when several do
     */
    public Directory unique() {
      if (directories.isEmpty()) {
        throw new Violation.Unplaced(type);
      }
      if (directories.size() > 1) {
        throw new Violation.Ambiguous(type, directories.stream().map(Directory::address).toList());
      }
      return directories.getFirst();
    }
  }
}
