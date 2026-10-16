package cat.tabbie.sdk.album.revision;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.platform.Package.Manager;
import cat.tabbie.sdk.platform.Relative;
import cat.tabbie.sdk.platform.Toolchain;
import lombok.NonNull;

/**
 * An immutable description of a change to a file's existence, a text file's
 * content, or a package claim. Describing an image performs no I/O, and images
 * track no filesystem metadata. The preimage exchanges before and after
 * states. Scrutinizing either description reads its target without changing
 * it; applying it belongs to its consumer. A replacement does not authorize
 * overwriting observed content.
 *
 * @param <State> underlying existence, content, or claim state
 */
public sealed interface Image<State extends Image.Alteration> {

  /**
   * Scrutinizes this image against its target as it is now, without changing
   * it: whether the target is in this image's before state and acting could
   * bring it to the after state. A target that no longer matches fails rather
   * than throws; {@link Scrutiny.Fail} lists what fails each kind of image.
   *
   * @return pass carrying the after state, or failure carrying the state found,
   *         or null when the found state cannot be described
   * @throws IOException when the target cannot be checked, including a
   *                     world-scoped file whose world mount is not known
   *
   * @apiNote A file target is read at its {@link Relative#relativePath() native
   *          relative path}, beneath the managed installation's working
   *          directory. World-scoped paths are resolved beneath the context
   *          mount first, as
   *          {@link cat.tabbie.sdk.merchant.Installer.Context#resolve(Relative)
   *          Installer.Context.resolve} does.
   */
  Scrutiny<State> scrutinize() throws IOException;

  /**
   * Target this image changes
   *
   * @return changed file or package
   */
  @NonNull
  Target target();

  /**
   * Expected existence, content, or claim before the change
   *
   * @return expected state before
   */
  @NonNull
  State before();

  /**
   * Described existence, content, or claim after the change
   *
   * @return described state after
   */
  @NonNull
  State after();

  /**
   * Image with its before and after states exchanged
   *
   * @return image reversed
   */
  @NonNull
  Image<State> preimage();

  /**
   * Describes creation of a file from retained content, including zero bytes.
   *
   * @param path  target file
   * @param after retained content
   *
   * @return resulting image
   */
  static Image<File> create(@NonNull Relative path, @NonNull Reference.Captured after) {
    return new Installation(new File.Absent(), new File.Present(after), new Target.File(path));
  }

  /**
   * Describes replacement of a file deleted and succeeded by another file
   * created.
   *
   * @param path   target file
   * @param before retained content before
   * @param after  retained content after
   *
   * @return resulting image
   */
  static Image<File> replace(@NonNull Relative path, @NonNull Reference.Captured before,
      @NonNull Reference.Captured after) {
    return new Installation(new File.Present(before), new File.Present(after), new Target.File(path));
  }

  /**
   * Describes deletion of a file whose complete content matches the reference.
   *
   * @param path   target file
   * @param before retained content before
   *
   * @return resulting image
   */
  static Image<File> delete(@NonNull Relative path, @NonNull Reference.Captured before) {
    return new Installation(new File.Present(before), new File.Absent(), new Target.File(path));
  }

  /**
   * Describes changes to an existing file between explicit fragment states. The
   * fragment lists pair up into chunks.
   *
   * @param path   target file
   * @param before text before
   * @param after  text after
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull Text before, @NonNull Text after) {
    return new Configuration(before, after, new Target.File(path));
  }

  /**
   * Describes changes to an existing file. Each chunk contributes a fragment on
   * each side; the chunks' insertion/deletion ordering is not retained.
   *
   * @param path   target file
   * @param chunks changed chunks
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull List<Chunk> chunks) {
    return new Configuration(chunks, new Target.File(path));
  }

  /**
   * Describes text differences with three surrounding context lines.
   *
   * @param path   target file
   * @param before text before
   * @param after  text after
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull String before, @NonNull String after) {
    return configure(path, Chunk.diff(before, after));
  }

  /**
   * Retains only the differing regions and their context, not complete content
   * expectations. Equal inputs produce empty fragment lists: the file must
   * exist, but no particular content is described.
   *
   * @param path         target file
   * @param before       text before
   * @param after        text after
   * @param contextLines number of context lines to keep
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull String before, @NonNull String after,
      int contextLines) {
    return configure(path, Chunk.diff(before, after, contextLines));
  }

  /**
   * Describes taking a claim on a package tabbie did not claim before.
   *
   * @param pack package recipe to claim
   *
   * @return resulting image
   *
   * @apiNote This factory makes no claims on the install and uninstall pathways,
   *          {@link #provision(Package, Map, Map)} is always preferred.
   */
  static Image<Claim> provision(@NonNull Package pack) {
    return new Provision(new Claim.Absent(), new Claim.Present(), new Target.Pack(pack));
  }

  /**
   * Describes taking a claim on a package tabbie did not claim before. Each
   * state carries the options used to reach it.
   *
   * @param pack    package recipe as claimed
   * @param install options for installing, by manager
   * @param undo    options for uninstalling again, by manager
   *
   * @return resulting image
   */
  static Image<Claim> provision(@NonNull Package pack, @NonNull Map<Manager, List<String>> install,
      @NonNull Map<Manager, List<String>> undo) {
    return new Provision(new Claim.Absent(undo), new Claim.Present(install), new Target.Pack(pack));
  }

  /**
   * Describes releasing tabbie's claim on a package claimed with this recipe.
   *
   * @param pack package recipe as claimed
   *
   * @return resulting image
   *
   * @apiNote This factory makes no claims on the install and uninstall pathways,
   *          {@link #deprovision(Package, Map, Map)} is always preferred.
   */
  static Image<Claim> deprovision(@NonNull Package pack) {
    return new Provision(new Claim.Present(), new Claim.Absent(), new Target.Pack(pack));
  }

  /**
   * Describes releasing tabbie's claim on a package claimed with this recipe.
   * Each state carries the options used to reach it.
   *
   * @param pack      package recipe as claimed
   * @param uninstall options for uninstalling, by manager
   * @param undo      options for installing again, by manager
   *
   * @return resulting image
   */
  static Image<Claim> deprovision(@NonNull Package pack, @NonNull Map<Manager, List<String>> uninstall,
      @NonNull Map<Manager, List<String>> undo) {
    return new Provision(new Claim.Present(undo), new Claim.Absent(uninstall), new Target.Pack(pack));
  }

  /**
   * Checks that images can be applied in any order: every file destination and
   * every package appears once, and no file destination is also used as a
   * directory under the same mount.
   *
   * @param images generated images
   * @throws IllegalArgumentException when generated images cannot be applied in
   *                                  any order
   */
  static void validate(@NonNull Collection<? extends Image<?>> images) {
    Map<Relative, Image<?>> files = new LinkedHashMap<>();
    Map<Package, Image<?>> packages = new LinkedHashMap<>();
    for (Image<?> image : images) {
      Image<?> clash = switch (image) {
        case Installation installation ->
          files.putIfAbsent(
              installation.target().relative(), installation);
        case Configuration configuration ->
          files.putIfAbsent(
              configuration.target().relative(), configuration);
        case Provision provision ->
          packages.putIfAbsent(
              provision.target().pkg(), provision);
      };
      if (clash != null) {
        throw new IllegalArgumentException("Images require unique targets: " + clash);
      }
    }
    for (Relative file : files.keySet()) {
      for (Relative parent = file.parent(); parent != null; parent = parent.parent()) {
        if (files.containsKey(parent)) {
          throw new IllegalArgumentException("A file destination may not also be used as a directory");
        }
      }
    }
  }

  /**
   * Describes the change that undoes a set. The preimages of a valid set are
   * valid: they change the same targets.
   *
   * @param images applied images
   * @return immutable set of preimages
   */
  static Set<Image<?>> preimage(@NonNull Collection<? extends Image<?>> images) {
    return images.stream().map(Image::preimage).collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Composes two changes of one target applied one after the other. A change
   * followed by its exact preimage cancels. Whole files and claims compose into
   * one change from the first's before state to the second's after state; a
   * claim taken and released again, or released and taken again, cancels even
   * when the options carried by its states differ. Text
   * changes compose through {@link Chunk#compose(List, List)}: separate regions
   * are kept, and overlapping regions are combined where both describe the
   * same intermediate lines. An installation and a configuration of the same
   * file stay sequential, because folding them would mean reading the retained
   * content. Whole-file states agree when their digest and size match; the
   * file name a capture was retained under is not part of the file's state.
   * Claims agree when both are held or both released, whatever options they
   * carry.
   *
   * @param first  earlier change
   * @param second later change, whose before state must agree with the
   *               first's after state
   * @return combined change, or empty when the pair has no net effect
   * @throws IllegalArgumentException when targets differ or states are
   *                                  discontinuous
   * @throws IrreducibleException     when one file is changed by different
   *                                  kinds of image
   */
  static Optional<Image<?>> compose(
      @NonNull Image<?> first,
      @NonNull Image<?> second) {

    // Must be similar targets
    if (!first.target().equals(second.target())) {
      throw new IllegalArgumentException(
          "Only changes of the same target compose: %s and %s".formatted(first.target(), second.target()));
    }

    // Installation, Configuration, Provision may not be cross-composed
    if (first.getClass() != second.getClass()) {
      throw new IrreducibleException(
          "Changes of %s by different kinds of image must stay sequential".formatted(first.target()));
    }

    // Second has a discontinuity error with first. Text changes describe only
    // selected regions, so Chunk.compose checks their agreement instead.
    if (!(first instanceof Configuration) && !same(first.after(), second.before())) {
      throw new IllegalArgumentException(
          "Changes are discontinuous at %s".formatted(first.target()));
    }

    // Second is a preimage of first. Text states are partial, so equal
    // fragments prove nothing and Chunk.compose decides instead.
    if (!(first instanceof Configuration) && same(first.before(), second.after())) {
      return Optional.empty();
    }

    return switch (first) {
      // Collapse [P -> A -> P] or [A -> P -> A]
      case Installation earlier ->
        Optional.of(new Installation(earlier.before(), ((Installation) second).after(), earlier.target()));

      // Collapse [P -> A -> P] or [A -> P -> A]; differing options alone are no net
      // change
      case Provision earlier -> {
        Claim before = earlier.before();
        Claim after = ((Provision) second).after();
        yield before.getClass() == after.getClass()
            ? Optional.empty()
            : Optional.of(new Provision(before, after, earlier.target()));
      }

      // Collapse [A ⊆ B] or [B ⊆ A]
      case Configuration earlier -> {
        List<Chunk> composed = Chunk.compose(earlier.chunks(), ((Configuration) second).chunks());
        yield composed.isEmpty()
            ? Optional.empty()
            : Optional.of(new Configuration(composed, earlier.target()));
      }
    };
  }

  /**
   * Whole-file states match by content and claims by whether they are held; other
   * states match exactly
   *
   * @param first  first alteration
   * @param second second alteration
   * @return whether first and second alteration are the same
   */
  private static boolean same(Alteration first, Alteration second) {
    if (first instanceof File.Present(Reference.Captured earlier)
        && second instanceof File.Present(Reference.Captured later)) {
      return earlier.sha256().equals(later.sha256()) && earlier.size() == later.size();
    }
    if (first instanceof Claim && second instanceof Claim) {
      return first.getClass() == second.getClass();
    }
    return first.equals(second);
  }

  /**
   * Locates a file target on this machine. Context-scoped paths lie beneath the
   * managed installation's working directory; world-scoped paths cannot be
   * located without their world mount.
   *
   * @param target target file
   * @return native relative path
   * @throws IOException when the target is world-scoped
   */
  private static Path locate(Target.File target) throws IOException {
    return switch (target.relative()) {
      case Relative.Root root -> root.relativePath();
      case Relative.World world -> throw new IOException(
          "A world-scoped file must be resolved beneath the context mount to be scrutinized: " + world.encoded());
    };
  }

  /**
   * Observes a file's existence and complete content, digesting the content
   * without retaining it.
   *
   * @param path     located file
   * @param fileName name the found content is described under
   * @return found state, or empty when the path is neither a file nor absent,
   *         such as a directory
   * @throws IOException when the file cannot be read
   */
  private static Optional<File> observe(Path path, String fileName) throws IOException {
    if (Files.isRegularFile(path)) {
      try (InputStream input = Files.newInputStream(path)) {
        return Optional.of(new File.Present(Extract.transfer(
            new Reference.Pending(fileName), input, OutputStream.nullOutputStream(), Observer.none())));
      }
    }
    return Files.exists(path)
        ? Optional.empty()
        : Optional.of(new File.Absent());
  }

  /**
   * Whether a file could be written at a path: an existing file must be
   * writable, and its nearest existing ancestor must be a writable directory
   * rather than a file used as one.
   *
   * @param path located file
   * @return whether the path is writable
   */
  private static boolean writable(Path path) {
    if (Files.exists(path) && !Files.isWritable(path)) {
      return false;
    }
    Path ancestor = path.toAbsolutePath().getParent();
    while (ancestor != null && !Files.exists(ancestor)) {
      ancestor = ancestor.getParent();
    }
    return ancestor != null && Files.isDirectory(ancestor) && Files.isWritable(ancestor);
  }

  /**
   * Runs a read-only package manager check, such as its probe or a verify,
   * without a shell, input, or output.
   *
   * @param command literal check
   * @return whether the program started and exited with status 0; a program
   *         that cannot start is absent
   * @throws IOException when interrupted while waiting for the check
   */
  private static boolean succeeds(Command command) throws IOException {
    List<String> line = command.render(new Command.Resolver() {

      @Override
      public Path path(@NonNull Relative relative) {
        throw new IllegalArgumentException("A package manager check names no scoped path.");
      }

      @Override
      public Path toolchain(@NonNull Toolchain toolchain) {
        throw new IllegalArgumentException("A package manager check names no toolchain.");
      }
    });
    Process process;
    try {
      process = new ProcessBuilder(line)
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .start();
    } catch (IOException exception) {
      return false;
    }
    try {
      // End of input, so a check that prompts cannot wait on it
      process.getOutputStream().close();
      return process.waitFor() == 0;
    } catch (InterruptedException exception) {
      process.destroy();
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while running " + line.getFirst(), exception);
    }
  }

  /**
   * Folds sequentially applied sets into the minimal set with the same net
   * effect. Targets whose changes cancel out disappear.
   *
   * @apiNote Folding replaces the images a consumer applied. A consumer that
   *          persisted install outcomes per applied provision, to decide
   *          whether a release uninstalls, must look them up by package name
   *          rather than by the folded image.
   *
   * @param changes sets in application order, each individually valid
   * @return immutable valid net change
   * @throws IllegalArgumentException when a set is invalid, changes are
   *                                  discontinuous, or the net change is order
   *                                  dependent and must stay sequential, such
   *                                  as a file replaced by a directory of the
   *                                  same name
   * @throws IrreducibleException     when one file is changed by different
   *                                  kinds of image
   */
  static Set<Image<?>> reduce(@NonNull List<? extends Collection<? extends Image<?>>> changes) {
    Map<Target, Image<?>> net = new LinkedHashMap<>();
    // Earlier halves of cancelled pairs: a later change of the same kind must
    // continue from the state its target returned to
    Map<Target, Image<?>> cancelled = new HashMap<>();
    for (Collection<? extends Image<?>> change : changes) {
      validate(change);
      for (Image<?> image : change) {
        Target target = image.target();
        Image<?> previous = net.remove(target);
        if (previous == null) {
          Image<?> settled = cancelled.remove(target);
          if (settled != null && settled.getClass() == image.getClass() && !(image instanceof Configuration)
              && !same(settled.before(), image.before())) {
            throw new IllegalArgumentException("Changes are discontinuous at %s".formatted(target));
          }
          net.put(target, image);
        } else {
          Optional<Image<?>> composed = compose(previous, image);
          if (composed.isPresent()) {
            net.put(target, composed.get());
          } else {
            cancelled.put(target, previous);
          }
        }
      }
    }
    validate(net.values());
    return Set.copyOf(net.values());
  }

  /**
   * Describes the target of this operation
   */
  sealed interface Target {

    /**
     * A file, by its relative path
     *
     * @param relative target path beneath, never equal to, its mount
     */
    record File(@NonNull Relative relative) implements Target {

      /**
       * Rejects the mount itself as a file.
       */
      public File {
        if (relative.isMount()) {
          throw new IllegalArgumentException("A file target must lie beneath its mount.");
        }
      }
    }

    /**
     * A package, by its mapped install specifications
     *
     * @param pkg package
     */
    record Pack(@NonNull Package pkg) implements Target {
    }
  }

  /**
   * Describes the underlying state a change expects or produces: a file's
   * existence, a text file's content, or a package claim.
   */
  sealed interface Alteration {
  }

  /**
   * Complete file-content expectations, distinguished from absence.
   */
  sealed interface File extends Alteration {

    /**
     * Assertion that no file exists at the image path.
     */
    record Absent() implements File {
    }

    /**
     * Assertion of complete retained file content, including zero bytes.
     *
     * @param reference named, verified captured content
     */
    record Present(@NonNull Reference.Captured reference) implements File {
    }
  }

  /**
   * Selected ordered ranges of an existing text file, rather than a
   * complete-file snapshot. An empty fragment list asserts existence without
   * asserting any particular content.
   *
   * @param fragments immutable copied ranges, possibly empty
   */
  record Text(@NonNull List<Chunk.Fragment> fragments) implements Alteration {

    /**
     * Copies and validates fragment order and line termination.
     *
     * @throws IllegalArgumentException when fragments overlap or have invalid
     *                                  termination
     */
    public Text {
      fragments = List.copyOf(fragments);
      Chunk.validateFragments(fragments);
    }
  }

  /**
   * Whether tabbie holds a claim on a package. Each state carries, by manager,
   * the options used to reach it.
   */
  sealed interface Claim extends Alteration {

    /**
     * No claim is held.
     *
     * @param uninstallOption options for releasing a claim, by manager
     */
    record Absent(
        @NonNull Map<Package.Manager, List<String>> uninstallOption) implements Claim {

      /**
       * Describes no claim, reached without extra options.
       */
      public Absent() {
        this(Map.of());
      }

      /**
       * Copies the options.
       */
      public Absent {
        uninstallOption = copy(uninstallOption);
      }
    }

    /**
     * A claim is held.
     *
     * @param installOption options for taking the claim, by manager
     */
    record Present(
        @NonNull Map<Package.Manager, List<String>> installOption) implements Claim {

      /**
       * Describes a held claim, reached without extra options.
       */
      public Present() {
        this(Map.of());
      }

      /**
       * Copies the options.
       */
      public Present {
        installOption = copy(installOption);
      }
    }

    private static Map<Package.Manager, List<String>> copy(Map<Package.Manager, List<String>> options) {
      return options.entrySet().stream()
          .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
  }

  /**
   * Creates, deletes, or replaces a whole file. Both sides may be present, but
   * both sides may not be absent. Equal references describe an unchanged file.
   *
   * <p>
   * Scrutinizing it digests the file without retaining it. It passes when the
   * file's absence, or its digest and size, match the before state and the
   * path can be written. Whether the after content is still retained is the
   * consumer's check, since an image does not know its retention backend.
   *
   * @param before complete content or absence expected before the change
   * @param after  complete content or absence described after the change
   * @param target target file
   */
  record Installation(
      @NonNull File before, @NonNull File after, @NonNull Target.File target)
      implements Image<Image.File> {

    /**
     * Rejects an absence-to-absence description.
     *
     * @throws IllegalArgumentException when both sides are absent
     */
    public Installation {
      if (before instanceof File.Absent && after instanceof File.Absent) {
        throw new IllegalArgumentException("Files may not both be absent!");
      }
    }

    @Override
    public Installation preimage() {
      return new Installation(after, before, target);
    }

    @Override
    public Scrutiny<File> scrutinize() throws IOException {
      Path path = locate(target);
      Optional<File> found = observe(path, target.relative().components().getLast());
      // A directory at the path has no file state to report
      if (found.isEmpty()) {
        return new Scrutiny.Fail<>();
      }
      if (!same(before, found.get())) {
        return new Scrutiny.Fail<>(found.get());
      }
      return writable(path)
          ? new Scrutiny.Pass<>(after)
          : new Scrutiny.Fail<>();
    }
  }

  /**
   * Changes selected ranges of an existing text file, stored as corresponding
   * chunks. A configuration never creates or deletes a file; an installation
   * does.
   *
   * <p>
   * Scrutinizing it reads the file as UTF-8 text. It passes when each chunk's
   * before fragment matches the file's lines at its range, exact endings
   * included; lines outside every chunk are not compared. A failure carries the
   * file's complete current text, or nothing when the file is gone.
   *
   * @param chunks text changes
   * @param target target file
   */
  record Configuration(
      @NonNull List<Chunk> chunks, @NonNull Target.File target)
      implements Image<Image.Text> {

    /**
     * Copies and validates the chunks.
     */
    public Configuration {
      chunks = List.copyOf(chunks);
      Chunk.validateChunks(chunks);
    }

    /**
     * Describes the change between two fragment states of an existing file.
     *
     * @param before text before
     * @param after  text after
     * @param target target file
     */
    public Configuration(
        @NonNull Text before, @NonNull Text after, @NonNull Target.File target) {

      this(Chunk.between(before.fragments(), after.fragments()), target);
    }

    @Override
    public Text before() {
      return new Text(
          chunks.stream()
              .map(Chunk::before)
              .toList());
    }

    @Override
    public Text after() {
      return new Text(
          chunks.stream()
              .map(Chunk::after)
              .toList());
    }

    @Override
    public Configuration preimage() {
      return new Configuration(
          chunks.stream()
              .map(Chunk::invert)
              .toList(),
          target);
    }

    @Override
    public Scrutiny<Text> scrutinize() throws IOException {
      Path path = locate(target);
      if (!Files.isRegularFile(path)) {
        return new Scrutiny.Fail<>();
      }
      String text = Files.readString(path, StandardCharsets.UTF_8);
      List<Chunk.Change.Line> lines = Chunk.linesOf(text);
      boolean matches = chunks.stream()
          .map(Chunk::before)
          .allMatch(fragment -> fragment.range().end() <= lines.size()
              && lines.subList(fragment.range().start(), fragment.range().end()).equals(fragment.lines()));
      return matches
          ? new Scrutiny.Pass<>(after())
          : new Scrutiny.Fail<>(new Text(Chunk.fragmentsOf(text)));
    }
  }

  /**
   * Takes or releases a package claim. Both sides may not be absent. When both
   * sides carry options, at least one manager must appear on both sides, so the
   * change can be undone with the manager that made it.
   *
   * <p>
   * Scrutinizing it runs the verify command of each manager carrying the
   * package. A claim is held when one of them reports the package installed
   * through its own spelling; a package another manager installed is not
   * claimed. It passes when that matches the before state and a manager on
   * this machine, as its probe reports, can make the change: any manager
   * carrying the package takes a claim, and a held claim changes through a
   * manager holding it.
   *
   * @param before claim expected before the change
   * @param after  claim described after the change
   * @param target claimed package
   */
  record Provision(@NonNull Claim before, @NonNull Claim after, @NonNull Target.Pack target)
      implements Image<Image.Claim> {

    /**
     * Rejects an absence-to-absence description and options that share no
     * manager.
     *
     * @throws IllegalArgumentException when both sides are absent, or both sides
     *                                  carry options for disjoint managers
     */
    public Provision {
      if (before instanceof Claim.Absent && after instanceof Claim.Absent) {
        throw new IllegalArgumentException("A provision changes at least one package");
      }

      Map<Package.Manager, List<String>> uninstall = switch (before) {
        case Claim.Absent(var paths) -> paths;
        case Claim.Present(var paths) -> paths;
      };
      Map<Package.Manager, List<String>> install = switch (after) {
        case Claim.Absent(var paths) -> paths;
        case Claim.Present(var paths) -> paths;
      };

      if (!uninstall.isEmpty() && !install.isEmpty()
          && Collections.disjoint(uninstall.keySet(), install.keySet())) {
        throw new IllegalArgumentException("There must be at least one valid install-uninstall path");
      }
    }

    @Override
    public @NonNull Image<Claim> preimage() {
      return new Provision(after, before, target);
    }

    @Override
    public Scrutiny<Claim> scrutinize() throws IOException {
      Package pack = target.pkg();
      List<Manager> carrying = pack.specifications().keySet().stream()
          .sorted()
          .toList();
      // Each manager verifies its own spelling, since another manager's install is not tabbie's claim
      List<Manager> holding = new ArrayList<>();
      for (Manager manager : carrying) {
        if (succeeds(manager.verify(pack.specifications().get(manager)))) {
          holding.add(manager);
        }
      }
      Claim found = holding.isEmpty()
          ? new Claim.Absent()
          : new Claim.Present();
      if (!same(before, found)) {
        return new Scrutiny.Fail<>(found);
      }
      for (Manager manager : before instanceof Claim.Absent ? carrying : holding) {
        if (succeeds(manager.probe())) {
          return new Scrutiny.Pass<>(after);
        }
      }
      return new Scrutiny.Fail<>();
    }
  }

  /**
   * Signals that changes of one target cannot be folded without observing the
   * complete content, for example an installation followed by a configuration
   * of the same file. The caller keeps the changes sequential or observes the
   * whole file instead.
   */
  final class IrreducibleException extends IllegalStateException {

    /**
     * Serialization version.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Reports changes that cannot be folded.
     *
     * @param message description naming the target
     */
    public IrreducibleException(String message) {
      super(message);
    }

    /**
     * Reports changes that cannot be folded, with the failure that revealed it.
     *
     * @param message   description naming the target
     * @param throwable underlying failure
     */
    public IrreducibleException(String message, Throwable throwable) {
      super(message, throwable);
    }
  }
}
