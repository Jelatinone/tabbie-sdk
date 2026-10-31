package cat.tabbie.sdk.platform.layout;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * An immutable model of a root, such as a Cat: the files and directories it
 * is meant to hold, at addresses relative to the model's root, and the
 * {@link Rule rules} that hold at each. A layout is made up rather than
 * backed. Nothing in it exists and nothing reads a file, so content is
 * placed against it alone and described as images; whatever already exists
 * where an image goes is found when the image is exposed or rehearsed. A
 * layout only grows: adding a declaration or mounting another layout returns
 * a new one.
 */
public sealed interface Layout extends Shape permits Layout.File, Layout.Directory {

  @Override
  @NonNull
  Optional<Layout.Directory> parent();

  /**
   * Returns the rules this place is declared with.
   *
   * @return immutable rules, possibly empty
   */
  @NonNull
  Set<Rule> rules();

  /**
   * Returns the permissions that hold at this place: those its rules grant,
   * or, when they grant none, those that hold at the directory above it. A
   * root that grants none grants nothing.
   *
   * @return immutable permissions, possibly empty
   */
  @NonNull
  default Set<Rule.Permission> permissions() {
    Set<Rule.Permission> granted = rules().stream()
        .filter(Rule.Permission.class::isInstance)
        .map(Rule.Permission.class::cast)
        .collect(Collectors.toUnmodifiableSet());
    return granted.isEmpty()
        ? parent().map(Layout::permissions).orElse(Set.of())
        : granted;
  }

  /**
   * Whether a permission holds at this place.
   *
   * @param permission permission to check
   * @return true when it holds
   */
  default boolean permits(@NonNull Rule.Permission permission) {
    return permissions().contains(permission);
  }

  @Override
  @NonNull
  default Layout locate(@NonNull Address tail) {
    return (Layout) Shape.super.locate(tail);
  }

  @Override
  @NonNull
  default Layout locate(@NonNull String... names) {
    return locate(Address.of(names));
  }

  /**
   * A declared file, such as a configuration file a model makes editable. Its
   * rules grant permissions alone, since a file accepts no content.
   *
   * @param parent  directory above it, empty for a file not yet beneath one
   * @param address address of the file
   * @param rules   permissions it is declared with
   */
  record File(
      @NonNull Optional<Layout.Directory> parent,
      @NonNull Address address,
      @NonNull Set<Rule> rules) implements Layout, Shape.File {

    /**
     * Copies the rules, and checks the address and that no rule accepts
     * content.
     *
     * @throws IllegalArgumentException when the address is the root, the file
     *                                  does not lie directly beneath its
     *                                  parent, or a rule accepts content
     */
    public File {
      rules = Set.copyOf(rules);
      if (address.isRoot()) {
        throw new IllegalArgumentException("Only a directory is declared at the root.");
      }
      if (parent.isPresent() && !address.parent().equals(parent.map(Layout::address))) {
        throw new IllegalArgumentException("A declared file lies directly beneath its parent.");
      }
      if (rules.stream().anyMatch(Rule.Accepts.class::isInstance)) {
        throw new IllegalArgumentException("A file accepts no content.");
      }
    }

    /**
     * Declares a file not yet beneath a directory.
     *
     * @param address address of the file
     * @param rules   permissions it is declared with
     */
    public File(@NonNull Address address, @NonNull Set<Rule> rules) {
      this(Optional.empty(), address, rules);
    }
  }

  /**
   * A declared directory, such as {@code mods}, which may accept content
   * through its {@link Rule.Accepts} rules. It holds the declarations directly
   * beneath it without their parents, and {@link #children()} returns them
   * beneath this directory.
   *
   * @param parent      directory above it, empty for a model's root or a
   *                    directory not yet beneath one
   * @param address     address of the directory
   * @param rules       rules it is declared with
   * @param directories directories declared directly beneath it
   * @param files       files declared directly beneath it
   */
  record Directory(
      @NonNull Optional<Layout.Directory> parent,
      @NonNull Address address,
      @NonNull Set<Rule> rules,
      @NonNull List<Layout.Directory> directories,
      @NonNull List<Layout.File> files) implements Layout, Shape.Directory {

    /**
     * Copies the rules and declarations, and checks where they lie.
     *
     * @throws IllegalArgumentException when the directory does not lie
     *                                  directly beneath its parent, or a
     *                                  declaration does not lie directly
     *                                  beneath it or shares a name with
     *                                  another
     */
    public Directory {
      rules = Set.copyOf(rules);
      directories = List.copyOf(directories);
      files = List.copyOf(files);
      if (parent.isPresent() && !address.parent().equals(parent.map(Layout::address))) {
        throw new IllegalArgumentException("A declared directory lies directly beneath its parent.");
      }
      Optional<Address> above = Optional.of(address);
      List<Layout> declarations = Stream.<Layout>concat(directories.stream(), files.stream()).toList();
      if (declarations.stream().anyMatch(declaration -> !declaration.address().parent().equals(above))) {
        throw new IllegalArgumentException("A declaration lies directly beneath its directory.");
      }
      if (declarations.stream().map(Layout::name).distinct().count() != declarations.size()) {
        throw new IllegalArgumentException("A directory declares each name only once.");
      }
    }

    /**
     * Declares a directory not yet beneath another, such as a model's root.
     *
     * @param address     address of the directory
     * @param rules       rules it is declared with
     * @param directories directories declared directly beneath it
     * @param files       files declared directly beneath it
     */
    public Directory(
        @NonNull Address address,
        @NonNull Set<Rule> rules,
        @NonNull List<Layout.Directory> directories,
        @NonNull List<Layout.File> files) {
      this(Optional.empty(), address, rules, directories, files);
    }

    /**
     * Returns the declarations directly beneath this directory, each with this
     * directory as its parent: directories first, then files.
     *
     * @return declarations, possibly empty
     */
    @Override
    public List<Layout> children() {
      Optional<Layout.Directory> self = Optional.of(this);
      return Stream.<Layout>concat(
          directories.stream()
              .map(directory -> new Layout.Directory(
                  self, directory.address(), directory.rules(), directory.directories(), directory.files())),
          files.stream()
              .map(file -> new Layout.File(self, file.address(), file.rules())))
          .toList();
    }

    @Override
    public Optional<Layout> child(@NonNull String name) {
      return children().stream()
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

    /**
     * Finds the directories that accept content of a type, this one included,
     * a directory before its children.
     *
     * @param type type of the content to place
     * @return the accepting directories, possibly none
     */
    @NonNull
    public Accepting find(@NonNull Class<?> type) {
      return new Accepting(type, accepting(type).toList());
    }

    private Stream<Layout.Directory> accepting(Class<?> type) {
      Stream<Layout.Directory> own = accepts(type) ? Stream.of(this) : Stream.empty();
      return Stream.concat(own, children().stream()
          .filter(Layout.Directory.class::isInstance)
          .map(Layout.Directory.class::cast)
          .flatMap(child -> child.accepting(type)));
    }

    /**
     * Requires the model to allow creating a file at a tail beneath this
     * directory: no declared file lies on the way, no directory is declared
     * at the tail itself, and the place the file goes is
     * {@link Rule.Permission#CREATABLE creatable}, as the deepest declaration
     * on the way grants. Whether anything exists there already is not the
     * model's to say.
     *
     * @param tail address of the file relative to this directory
     * @return this directory
     * @throws Violation.Terminal       when a declared file lies on the way
     * @throws Violation.Occupied       when a directory is declared at the tail
     * @throws Violation.Forbidden      when the place is not creatable
     * @throws IllegalArgumentException when the tail names this directory
     */
    @Override
    @NonNull
    public Layout.Directory require(@NonNull Address tail) {
      if (tail.isRoot()) {
        throw new IllegalArgumentException("A file is created beneath a directory, not in its place.");
      }
      List<String> names = tail.components();
      Address rest = Address.root().resolve(names.subList(1, names.size()));
      Optional<Layout> declared = child(names.getFirst());
      if (declared.isPresent() && !rest.isRoot()) {
        switch (declared.get()) {
          case Layout.Directory directory -> directory.require(rest);
          case Layout.File file -> throw new Violation.Terminal(file.address(), rest.components().getFirst());
        }
        return this;
      }
      declared.filter(Layout.Directory.class::isInstance).ifPresent(directory -> {
        throw new Violation.Occupied(directory.address());
      });
      if (!declared.orElse(this).permits(Rule.Permission.CREATABLE)) {
        throw new Violation.Forbidden(address.resolve(tail), Rule.Permission.CREATABLE);
      }
      return this;
    }

    /**
     * Declares a directory directly beneath this one.
     *
     * @param name  name of the directory
     * @param rules rules it is declared with
     * @return this directory with the other declared beneath it
     * @throws IllegalArgumentException when the name is declared already
     */
    @NonNull
    public Layout.Directory withDirectory(@NonNull String name, @NonNull Set<Rule> rules) {
      Layout.Directory declared = new Layout.Directory(address.resolve(name), rules, List.of(), List.of());
      return new Layout.Directory(parent, address, this.rules,
          Stream.concat(directories.stream(), Stream.of(declared)).toList(), files);
    }

    /**
     * Declares a file directly beneath this directory.
     *
     * @param name  name of the file
     * @param rules permissions it is declared with
     * @return this directory with the file declared beneath it
     * @throws IllegalArgumentException when the name is declared already, or a
     *                                  rule accepts content
     */
    @NonNull
    public Layout.Directory withFile(@NonNull String name, @NonNull Set<Rule> rules) {
      Layout.File declared = new Layout.File(address.resolve(name), rules);
      return new Layout.Directory(parent, address, this.rules, directories,
          Stream.concat(files.stream(), Stream.of(declared)).toList());
    }

    /**
     * Mounts another layout at an address beneath this directory, declaring
     * its root and everything beneath it there, as core mounts a World's
     * layout on the World's main directory. Directories on the way that are
     * not declared yet are declared without rules. Mounting only adds, so
     * nothing may be declared at the address yet.
     *
     * @param at     address beneath this directory
     * @param layout layout declared from its own root
     * @return this directory with the other layout mounted beneath it
     * @throws IllegalArgumentException when the address names this directory,
     *                                  something is declared there or a file
     *                                  is declared on the way, or the layout is
     *                                  not declared from its root
     */
    @NonNull
    public Layout.Directory mount(@NonNull Address at, @NonNull Layout.Directory layout) {
      if (at.isRoot() || !layout.address().isRoot()) {
        throw new IllegalArgumentException("A layout declared from its root is mounted beneath a directory.");
      }
      String name = at.components().getFirst();
      Address rest = Address.root().resolve(at.components().subList(1, at.components().size()));
      Optional<Layout.Directory> declared = directories.stream()
          .filter(directory -> directory.name().equals(name))
          .findFirst();
      if (files.stream().anyMatch(file -> file.name().equals(name)) || rest.isRoot() && declared.isPresent()) {
        throw new IllegalArgumentException("A layout is mounted only where nothing is declared yet.");
      }
      Layout.Directory mounted = rest.isRoot()
          ? layout.rebased(address.resolve(name))
          : declared
              .orElseGet(() -> new Layout.Directory(address.resolve(name), Set.of(), List.of(), List.of()))
              .mount(rest, layout);
      return new Layout.Directory(parent, address, rules, Stream.concat(directories.stream()
          .filter(directory -> !directory.name().equals(name)), Stream.of(mounted)).toList(), files);
    }

    /**
     * Declares this directory and everything beneath it at another address.
     */
    private Layout.Directory rebased(Address base) {
      return new Layout.Directory(base, rules,
          directories.stream()
              .map(directory -> directory.rebased(base.resolve(directory.name())))
              .toList(),
          files.stream()
              .map(file -> new Layout.File(base.resolve(file.name()), file.rules()))
              .toList());
    }
  }

  /**
   * The declared directories that accept one type of content, as
   * {@link Directory#find(Class)} found them. Several directories may accept
   * the same type: work placing the content picks among them, or requires
   * {@link #unique() exactly one}.
   *
   * @param type        type of the content to place
   * @param directories accepting directories, possibly none
   */
  record Accepting(@NonNull Class<?> type, @NonNull List<Layout.Directory> directories) {

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
      for (Layout.Directory directory : directories) {
        if (!directory.accepts(type)) {
          throw new Violation.Unaccepted(directory.address(), type);
        }
      }
      if (directories.stream().map(Layout::address).distinct().count() != directories.size()) {
        throw new IllegalArgumentException("An accepting directory is listed only once.");
      }
    }

    /**
     * Requires exactly one accepting directory, for work that places content
     * the same way wherever the model puts it.
     *
     * @return the only accepting directory
     * @throws Violation.Unplaced  when no directory accepts the type
     * @throws Violation.Ambiguous when several do
     */
    @NonNull
    public Layout.Directory unique() {
      if (directories.isEmpty()) {
        throw new Violation.Unplaced(type);
      }
      if (directories.size() > 1) {
        throw new Violation.Ambiguous(type, directories.stream().map(Layout::address).toList());
      }
      return directories.getFirst();
    }
  }
}
