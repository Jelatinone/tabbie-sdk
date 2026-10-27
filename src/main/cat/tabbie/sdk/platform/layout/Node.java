package cat.tabbie.sdk.platform.layout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * A place in the tree of a root, such as a Cat: a {@link File} or a
 * {@link Directory} at its own {@link Address}, beneath the directory standing
 * for the root. Every node is of one kind and in one state:
 * <ul>
 * <li>A {@link Declared} node is named by a layout, such as the one a
 * distribution build declares. It is in the tree whether or not it exists
 * yet, and holds the {@link Rule rules} it was declared with.</li>
 * <li>A {@link Present} node exists without being declared, such as a mod in
 * {@code mods}. It holds no rules of its own.</li>
 * </ul>
 * A place that is neither declared nor present has no node.
 *
 * <p>
 * A {@link cat.tabbie.sdk.album.model.Plate Plate} implements the nodes of its
 * tree as views of its own files, so a change carried out on the plate shows
 * in its tree at once, and the same work sees a rehearsal's overlay or the Cat
 * itself depending only on the plate it runs on. Work reaches files only
 * through the {@link Lease leases} nodes grant: a file's to read or change it,
 * and a directory's to create a file beneath it. A plate implements only each
 * node's {@link #address()}, {@link #parent()} and {@link #path()}, a
 * directory's {@link Directory#children() children} and
 * {@link Directory#claim(Address) claims}, a file's leases, and a declared
 * node's {@link Declared#rules() rules}; everything else here follows from
 * those. Plates' nodes are safe for concurrent use.
 *
 * <p>
 * Nodes carry no meaning beyond their addresses and rules, so a tree never
 * needs a special kind of node for one game feature.
 * {@link #locate(Address) Locating} goes down to exactly one node,
 * {@link #parent()} and {@link #root()} go up, and
 * {@link Directory#find(Class) finding} lists the declared directories that
 * accept a type. A node's {@link #address()} leaves the tree as a plain
 * address, and locating that address from the root finds the node again.
 */
public sealed interface Node permits Node.File, Node.Directory, Node.Declared, Node.Present {

  /**
   * Returns where this node is.
   *
   * @return address beneath the root, the root address only for the directory
   *         standing for the root
   */
  @NonNull
  Address address();

  /**
   * Returns the directory directly above this node.
   *
   * @return parent directory, empty for the directory standing for the root
   */
  @NonNull
  Optional<Node.Directory> parent();

  /**
   * Returns this node's physical path, only for handing it to something
   * outside the tree, such as a command an outside process runs or a ZIP
   * reader. Work reads and writes files through leases, never through this
   * path: while rehearsing, it names the real file rather than the overlay.
   *
   * @return physical path
   * @throws IllegalStateException when no plate has mounted this node
   */
  @NonNull
  Path path();

  /**
   * Returns the directory standing for the root of this node's tree, by
   * following {@link #parent()} up as far as it goes.
   *
   * @return root directory, this node itself when it stands for the root
   * @throws IllegalStateException when this is a file beneath no directory,
   *                               such as a declared file not yet placed in
   *                               a layout
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
   * Returns this node's name, the last component of its address.
   *
   * @return single filename, empty only for the directory standing for the
   *         root
   */
  @NonNull
  default String name() {
    return address().fileName().orElse("");
  }

  /**
   * Returns the permissions that hold at this node: those it was declared
   * with, or, when it declares none, those that hold at the directory above
   * it. A node that declares none, as no present node does, follows its
   * directory as that directory stands when it is checked, so a node added
   * later never lacks the permissions of the place it was added. A root that
   * declares none grants nothing.
   *
   * @return immutable permissions, possibly empty
   */
  @NonNull
  default Set<Rule.Permission> permissions() {
    Set<Rule.Permission> declared = switch (this) {
      case Node.Declared node -> node.rules().stream()
          .filter(Rule.Permission.class::isInstance)
          .map(Rule.Permission.class::cast)
          .collect(Collectors.toUnmodifiableSet());
      case Node.Present _ -> Set.of();
    };
    return declared.isEmpty()
        ? parent().map(Node::permissions).orElse(Set.of())
        : declared;
  }

  /**
   * Whether a permission holds at this node.
   *
   * @param permission permission to check
   * @return true when it holds
   */
  default boolean permits(@NonNull Rule.Permission permission) {
    return permissions().contains(permission);
  }

  /**
   * Requires a permission to hold at this node.
   *
   * @param permission permission the operation needs
   * @return this node
   * @throws Violation.Forbidden when it does not hold
   */
  @NonNull
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
  @NonNull
  default Node locate(@NonNull String... names) {
    return locate(Address.of(names));
  }

  /**
   * Locates the node at an address relative to this one, following the tree's
   * names one component at a time. It goes to exactly that node or fails; it
   * never settles for another node along the way.
   *
   * @param tail address relative to this node
   * @return the node there, or this node for the root address
   * @throws Violation.Missing  when the tree holds nothing at a step
   * @throws Violation.Terminal when a step leads beneath a file
   */
  @NonNull
  default Node locate(@NonNull Address tail) {
    Node reached = this;
    for (String name : tail.components()) {
      reached = switch (reached) {
        case Node.Directory directory -> directory.child(name)
            .orElseThrow(() -> new Violation.Missing(directory.address().resolve(name)));
        case Node.File file -> throw new Violation.Terminal(file.address(), name);
      };
    }
    return reached;
  }

  /**
   * Describes asking a layout no plate has mounted for what only a plate
   * binds.
   */
  private static IllegalStateException unmounted() {
    return new IllegalStateException("A layout no plate has mounted leases no file and binds no path.");
  }

  /**
   * A file. Nothing lies beneath a file, and a file accepts no content. A file
   * the tree holds is read and written through its own leases, never created
   * through its directory: a declared file not on disk yet is created by
   * {@link Lease.Write#replace(cat.tabbie.sdk.platform.Describe) replacing}
   * it, which needs it to be {@link Rule.Permission#CREATABLE creatable}.
   */
  sealed interface File extends Node permits Node.Declared.File, Node.Present.File {

    /**
     * Leases this file for reading.
     *
     * @return open lease, owned by the caller
     * @throws Refusal               with {@link Refusal.Held} when the plate
     *                               does not wait and the file is leased for
     *                               writing
     * @throws IOException           when the lease cannot be taken
     * @throws IllegalStateException when no plate has mounted this file
     */
    @NonNull
    Lease.Read read() throws IOException;

    /**
     * Leases this file for writing.
     *
     * @return open lease, owned by the caller
     * @throws Refusal               with {@link Refusal.Held} when the plate
     *                               does not wait and the file is leased
     * @throws IOException           when the lease cannot be taken
     * @throws IllegalStateException when no plate has mounted this file
     */
    @NonNull
    Lease.Write write() throws IOException;

    /**
     * Leases this file for reading and writing, for work that checks a file
     * before it changes it.
     *
     * @return open lease, owned by the caller
     * @throws Refusal               with {@link Refusal.Held} when the plate
     *                               does not wait and the file is leased
     * @throws IOException           when the lease cannot be taken
     * @throws IllegalStateException when no plate has mounted this file
     */
    @NonNull
    Lease.Mutual mutual() throws IOException;
  }

  /**
   * A directory, holding the nodes directly beneath it.
   */
  sealed interface Directory extends Node permits Node.Declared.Directory, Node.Present.Directory {

    /**
     * Lists the nodes directly beneath this directory: those declared there,
     * whether or not they exist yet, and those that exist there without being
     * declared. A plate lists what exists the first time something goes
     * beneath the directory, and keeps the listing current as its own leases
     * change it.
     *
     * @return uniquely named nodes, in the order the tree holds them, possibly
     *         empty
     */
    @NonNull
    List<Node> children();

    /**
     * Finds the node of a name directly beneath this directory.
     *
     * @param name single filename
     * @return the node there, or empty when the tree holds nothing of that
     *         name here
     */
    @NonNull
    default Optional<Node> child(@NonNull String name) {
      return children().stream()
          .filter(child -> child.name().equals(name))
          .findFirst();
    }

    /**
     * Finds the declared directories that accept content of a type, this one
     * included, in tree order: a directory before its children, and children
     * in the order they are held. Only declared directories accept content,
     * and every declared node lies beneath declared directories alone, so the
     * search follows declared directories only.
     *
     * @param type type of the content to place
     * @return the accepting directories, possibly none
     */
    @NonNull
    default Accepting find(@NonNull Class<?> type) {
      return new Accepting(type, accepting(type).toList());
    }

    /**
     * Lists this directory, if it accepts a type, and then the declared
     * directories beneath it that do, in tree order.
     */
    private Stream<Node.Declared.Directory> accepting(Class<?> type) {
      Stream<Node.Declared.Directory> own = this instanceof Node.Declared.Directory declared && declared.accepts(type)
          ? Stream.of(declared)
          : Stream.empty();
      return Stream.concat(own, children().stream()
          .filter(Node.Declared.Directory.class::isInstance)
          .map(Node.Directory.class::cast)
          .flatMap(child -> child.accepting(type)));
    }

    /**
     * Requires the tree to allow creating a file at a tail beneath this
     * directory. Following the tail as far as the tree holds nodes, every step
     * must be a directory and nothing may be held at the tail itself, and the
     * deepest directory reached must be
     * {@link Rule.Permission#CREATABLE creatable}: the directories created
     * above the file take their permissions from it.
     *
     * @param tail address of the file relative to this directory
     * @return this directory
     * @throws Violation.Terminal       when a step leads beneath a file
     * @throws Violation.Occupied       when the tree already holds a node at
     *                                  the tail
     * @throws Violation.Forbidden      when the deepest directory reached is
     *                                  not creatable
     * @throws IllegalArgumentException when the tail names this directory
     */
    @NonNull
    default Node.Directory require(@NonNull Address tail) {
      if (tail.isRoot()) {
        throw new IllegalArgumentException("A file is created beneath a directory, not in its place.");
      }
      List<String> names = tail.components();
      Node.Directory deepest = this;
      for (int step = 0; step < names.size(); step++) {
        Optional<Node> held = deepest.child(names.get(step));
        if (held.isEmpty()) {
          break;
        }
        if (step == names.size() - 1) {
          throw new Violation.Occupied(held.get().address());
        }
        String below = names.get(step + 1);
        deepest = switch (held.get()) {
          case Node.Directory directory -> directory;
          case Node.File file -> throw new Violation.Terminal(file.address(), below);
        };
      }
      deepest.require(Rule.Permission.CREATABLE);
      return this;
    }

    /**
     * Leases the place at a tail beneath this directory for creating a file
     * there, once the tree allows it, as {@link #require(Address)} checks.
     *
     * @apiNote The default checks the tree, then leases the place through
     *          {@link #claim(Address)}.
     * @param tail address of the file relative to this directory
     * @return open lease, owned by the caller
     * @throws Violation                when the tree does not allow the file,
     *                                  as {@link #require(Address)} refuses
     * @throws Refusal                  with {@link Refusal.Held} when the plate
     *                                  does not wait and the place is leased
     * @throws IOException              when the lease cannot be taken
     * @throws IllegalArgumentException when the tail names this directory
     * @throws IllegalStateException    when no plate has mounted this
     *                                  directory
     */
    @NonNull
    default Lease.Create create(@NonNull Address tail) throws IOException {
      require(tail);
      return claim(tail);
    }

    /**
     * Leases the place at a tail beneath this directory for creating a file
     * there, without checking the tree. Work creates files through
     * {@link #create(Address)}, which checks the tree first.
     *
     * @param tail address of the file relative to this directory, where the
     *             tree holds nothing
     * @return open lease, owned by the caller
     * @throws Refusal               with {@link Refusal.Held} when the plate
     *                               does not wait and the place is leased
     * @throws IOException           when the lease cannot be taken
     * @throws IllegalStateException when no plate has mounted this directory
     */
    @NonNull
    Lease.Create claim(@NonNull Address tail) throws IOException;
  }

  /**
   * A node a layout names, such as the {@code mods} directory a distribution
   * build declares. It is in the tree whether or not it exists yet, and a
   * plate never prunes it: a declared directory deleted from a Cat stays in
   * the tree with its rules, and creating beneath it makes it again. A layout
   * declares the directories above each node it declares, so every declared
   * node lies beneath declared directories alone.
   */
  sealed interface Declared extends Node permits Node.Declared.File, Node.Declared.Directory {

    /**
     * Returns the rules this node was declared with. A node declared without
     * permissions takes those of the directory above it, as
     * {@link Node#permissions()} says.
     *
     * @return immutable rules, possibly empty
     */
    @NonNull
    Set<Rule> rules();

    /**
     * A declared file, such as a configuration file a layout makes editable.
     * Its rules grant permissions alone, since a file accepts no content.
     */
    non-sealed interface File extends Node.File, Node.Declared {

      /**
       * A declared file in a layout that no plate has mounted, as a
       * distribution build declares one.
       *
       * @param parent  directory above it, empty for a declaration not yet
       *                beneath one
       * @param address address of the file
       * @param rules   permissions it is declared with
       */
      record Default(
          @NonNull Optional<Node.Directory> parent,
          @NonNull Address address,
          @NonNull Set<Rule> rules) implements Node.Declared.File {

        /**
         * Copies the rules, and checks the address and that no rule accepts
         * content.
         *
         * @throws IllegalArgumentException when the address is the root, the
         *                                  file does not lie directly beneath
         *                                  its parent, or a rule accepts
         *                                  content
         */
        public Default {
          rules = Set.copyOf(rules);
          if (address.isRoot()) {
            throw new IllegalArgumentException("Only a directory stands for the root.");
          }
          if (parent.isPresent() && !address.parent().equals(parent.map(Node::address))) {
            throw new IllegalArgumentException("A declared file lies directly beneath its parent.");
          }
          if (rules.stream().anyMatch(Rule.Accepts.class::isInstance)) {
            throw new IllegalArgumentException("A file accepts no content.");
          }
        }

        /**
         * Declares a file not yet beneath a directory, to declare in one.
         *
         * @param address address of the file
         * @param rules   permissions it is declared with
         */
        public Default(@NonNull Address address, @NonNull Set<Rule> rules) {
          this(Optional.empty(), address, rules);
        }

        /**
         * Refuses a path, since no plate has mounted this file.
         *
         * @throws IllegalStateException always
         */
        @Override
        public Path path() {
          throw unmounted();
        }

        /**
         * Refuses a lease, since no plate has mounted this file.
         *
         * @throws IllegalStateException always
         */
        @Override
        public Lease.Read read() {
          throw unmounted();
        }

        /**
         * Refuses a lease, since no plate has mounted this file.
         *
         * @throws IllegalStateException always
         */
        @Override
        public Lease.Write write() {
          throw unmounted();
        }

        /**
         * Refuses a lease, since no plate has mounted this file.
         *
         * @throws IllegalStateException always
         */
        @Override
        public Lease.Mutual mutual() {
          throw unmounted();
        }
      }
    }

    /**
     * A declared directory, such as {@code mods}, which may accept content
     * through its {@link Rule.Accepts} rules.
     */
    non-sealed interface Directory extends Node.Directory, Node.Declared {

      /**
       * Whether this directory accepts content of a type.
       *
       * @param type type of the content to place
       * @return true when one of its {@link Rule.Accepts} rules accepts the
       *         type
       */
      default boolean accepts(@NonNull Class<?> type) {
        return rules().stream()
            .anyMatch(rule -> rule instanceof Rule.Accepts accepts && accepts.accepts(type));
      }

      /**
       * A declared directory in a layout that no plate has mounted, as a
       * distribution build declares its layout. It holds the declarations
       * directly beneath it without their parents, and
       * {@link #children()} returns them beneath this directory, so walking a
       * layout from its root finds every parent in place. Nothing is present
       * in a layout, so a directory's children are its declarations alone. A
       * plate mounts a layout by wrapping each of its nodes in one of the
       * plate's own.
       *
       * @param parent      directory above it, empty for the root of a layout
       *                    or for a declaration not yet beneath one
       * @param address     address of the directory
       * @param rules       rules it is declared with
       * @param directories directories declared directly beneath it
       * @param files       files declared directly beneath it
       */
      record Default(
          @NonNull Optional<Node.Directory> parent,
          @NonNull Address address,
          @NonNull Set<Rule> rules,
          @NonNull List<Default> directories,
          @NonNull List<Node.Declared.File.Default> files) implements Node.Declared.Directory {

        /**
         * Copies the rules and declarations, and checks where they lie.
         *
         * @throws IllegalArgumentException when the directory does not lie
         *                                  directly beneath its parent, or a
         *                                  declaration does not lie directly
         *                                  beneath it or shares a name with
         *                                  another
         */
        public Default {
          rules = Set.copyOf(rules);
          directories = List.copyOf(directories);
          files = List.copyOf(files);
          if (parent.isPresent() && !address.parent().equals(parent.map(Node::address))) {
            throw new IllegalArgumentException("A declared directory lies directly beneath its parent.");
          }
          Optional<Address> above = Optional.of(address);
          List<Node.Declared> declarations = Stream.<Node.Declared>concat(directories.stream(), files.stream())
              .toList();
          if (declarations.stream().anyMatch(declaration -> !declaration.address().parent().equals(above))) {
            throw new IllegalArgumentException("A declaration lies directly beneath its directory.");
          }
          if (declarations.stream().map(Node::name).distinct().count() != declarations.size()) {
            throw new IllegalArgumentException("A directory declares each name only once.");
          }
        }

        /**
         * Declares a directory not yet beneath another, such as the root of a
         * layout.
         *
         * @param address     address of the directory
         * @param rules       rules it is declared with
         * @param directories directories declared directly beneath it
         * @param files       files declared directly beneath it
         */
        public Default(
            @NonNull Address address,
            @NonNull Set<Rule> rules,
            @NonNull List<Default> directories,
            @NonNull List<Node.Declared.File.Default> files) {
          this(Optional.empty(), address, rules, directories, files);
        }

        /**
         * Returns the declarations directly beneath this directory, each with
         * this directory as its parent: directories first, then files.
         *
         * @return declared nodes, possibly empty
         */
        @Override
        public List<Node> children() {
          Optional<Node.Directory> self = Optional.of(this);
          return Stream.<Node>concat(
              directories.stream()
                  .map(directory -> new Default(
                      self, directory.address(), directory.rules(), directory.directories(), directory.files())),
              files.stream()
                  .map(file -> new Node.Declared.File.Default(self, file.address(), file.rules())))
              .toList();
        }

        /**
         * Refuses a path, since no plate has mounted this directory.
         *
         * @throws IllegalStateException always
         */
        @Override
        public Path path() {
          throw unmounted();
        }

        /**
         * Refuses a lease, since no plate has mounted this directory.
         *
         * @throws IllegalStateException always
         */
        @Override
        public Lease.Create claim(@NonNull Address tail) {
          throw unmounted();
        }
      }
    }
  }

  /**
   * A node that exists without being declared, such as a mod in {@code mods}
   * or a World's files. It holds no rules of its own, so it always follows
   * the permissions of the directory above it, and it leaves the tree when it
   * is deleted. Only plates implement present nodes.
   */
  sealed interface Present extends Node permits Node.Present.File, Node.Present.Directory {

    /**
     * A file that exists without being declared.
     */
    non-sealed interface File extends Node.File, Node.Present {
    }

    /**
     * A directory that exists without being declared.
     */
    non-sealed interface Directory extends Node.Directory, Node.Present {
    }
  }

  /**
   * The declared directories that accept one type of content, as
   * {@link Directory#find(Class)} found them. Several directories may accept
   * the same type: work placing the content picks among them, or requires
   * {@link #unique() exactly one}.
   *
   * @param type        type of the content to place
   * @param directories accepting directories, in tree order, possibly none
   */
  record Accepting(@NonNull Class<?> type, @NonNull List<Node.Declared.Directory> directories) {

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
      for (Node.Declared.Directory directory : directories) {
        if (!directory.accepts(type)) {
          throw new Violation.Unaccepted(directory.address(), type);
        }
      }
      if (directories.stream().map(Node::address).distinct().count() != directories.size()) {
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
    @NonNull
    public Node.Declared.Directory unique() {
      if (directories.isEmpty()) {
        throw new Violation.Unplaced(type);
      }
      if (directories.size() > 1) {
        throw new Violation.Ambiguous(type, directories.stream().map(Node::address).toList());
      }
      return directories.getFirst();
    }
  }
}
