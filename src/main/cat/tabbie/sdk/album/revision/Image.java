package cat.tabbie.sdk.album.revision;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.album.model.Plate;
import cat.tabbie.sdk.album.repository.Stash;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.platform.layout.Lease;
import cat.tabbie.sdk.platform.layout.Node;
import cat.tabbie.sdk.platform.layout.Refusal;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * An immutable description of a change to one file: its existence and
 * complete content, or selected lines of its text. Describing an image
 * performs no I/O, and images track no filesystem metadata. The preimage
 * exchanges before and after states. Exposing an image carries its change out
 * on a {@link Plate}, after checking that its file is in the before state; a
 * replacement does not authorize overwriting content it did not expect.
 *
 * <p>
 * An image is always well formed: it changes something, and its file lies
 * beneath its root. Describing a change that is not throws a
 * {@link Malformation} saying why, so no image needs checking for a change
 * that does nothing.
 *
 * <p>
 * Images name content by {@link Reference.Captured reference}, never by its
 * bytes: the bytes live in the stash of the plate an image is exposed on.
 * Images describe files, not the machine they run on. What a Cat needs from
 * its machine, such as a system package, is data in its lockfile, which images
 * edit like any other text file.
 *
 * @param <State> underlying existence or content state
 */
public sealed interface Image<State extends Image.Alteration> {

  /**
   * Exposes this image on a plate: checks that its file is in the before
   * state, then brings it to the after state through the plate. A file that
   * no longer matches fails rather than throws, and is left unchanged;
   * {@link Scrutiny.Fail} lists what fails each kind of image. The same image
   * exposes alike on a rehearsal's plate and on an acting one.
   *
   * @param plate plate the change is carried out on
   * @return pass carrying the state the file reached, or failure carrying the
   *         state found or what refused the change
   * @throws IOException when the file cannot be read or the change fails
   *                     part-way
   */
  Scrutiny<State> expose(@NonNull Plate plate) throws IOException;

  /**
   * File this image changes.
   *
   * @return changed file, beneath the root of the plate it is exposed on
   */
  @NonNull
  Address address();

  /**
   * Expected existence or content before the change.
   *
   * @return expected state before
   */
  @NonNull
  State before();

  /**
   * Described existence or content after the change.
   *
   * @return described state after
   */
  @NonNull
  State after();

  /**
   * Image with its before and after states exchanged.
   *
   * @return image reversed
   */
  @NonNull
  Image<State> preimage();

  /**
   * Describes creation of a file from retained content, including zero bytes.
   *
   * @param address target file
   * @param after   retained content
   * @return resulting image
   * @throws Malformation.Root when the address is the root itself
   */
  static Image<File> create(@NonNull Address address, @NonNull Reference.Captured after) {
    return new Installation(new File.Absent(), new File.Present(after), address);
  }

  /**
   * Describes replacement of a file deleted and succeeded by another file
   * created.
   *
   * @param address target file
   * @param before  retained content before
   * @param after   retained content after
   * @return resulting image
   * @throws Malformation.Unchanged when both contents are the same
   * @throws Malformation.Root      when the address is the root itself
   */
  static Image<File> replace(@NonNull Address address, @NonNull Reference.Captured before,
      @NonNull Reference.Captured after) {
    return new Installation(new File.Present(before), new File.Present(after), address);
  }

  /**
   * Describes deletion of a file whose complete content matches the reference.
   *
   * @param address target file
   * @param before  retained content before
   * @return resulting image
   * @throws Malformation.Root when the address is the root itself
   */
  static Image<File> delete(@NonNull Address address, @NonNull Reference.Captured before) {
    return new Installation(new File.Present(before), new File.Absent(), address);
  }

  /**
   * Describes changes to an existing file between explicit fragment states. The
   * fragment lists pair up into chunks.
   *
   * @param address target file
   * @param before  text before
   * @param after   text after
   * @return resulting image
   * @throws Malformation.Unchanged when no fragment changes
   * @throws Malformation.Root      when the address is the root itself
   */
  static Image<Text> configure(@NonNull Address address, @NonNull Text before, @NonNull Text after) {
    return new Configuration(before, after, address);
  }

  /**
   * Describes changes to an existing file. Each chunk contributes a fragment on
   * each side; the chunks' insertion/deletion ordering is not retained.
   *
   * @param address target file
   * @param chunks  changed chunks
   * @return resulting image
   * @throws Malformation.Unchanged when no chunk edits its text
   * @throws Malformation.Root      when the address is the root itself
   */
  static Image<Text> configure(@NonNull Address address, @NonNull List<Chunk> chunks) {
    return new Configuration(chunks, address);
  }

  /**
   * Describes text differences with three surrounding context lines.
   *
   * @param address target file
   * @param before  text before
   * @param after   text after
   * @return resulting image
   * @throws Malformation.Unchanged when the texts are equal
   * @throws Malformation.Root      when the address is the root itself
   */
  static Image<Text> configure(@NonNull Address address, @NonNull String before, @NonNull String after) {
    return configure(address, Chunk.diff(before, after));
  }

  /**
   * Describes text differences, retaining only the differing regions and their
   * context rather than complete content expectations.
   *
   * @param address      target file
   * @param before       text before
   * @param after        text after
   * @param contextLines number of context lines to keep
   * @return resulting image
   * @throws Malformation.Unchanged when the texts are equal
   * @throws Malformation.Root      when the address is the root itself
   */
  static Image<Text> configure(@NonNull Address address, @NonNull String before, @NonNull String after,
      int contextLines) {
    return configure(address, Chunk.diff(before, after, contextLines));
  }

  /**
   * Checks that images can be exposed in any order, and so in parallel: every
   * file appears once, and no changed file lies beneath another.
   *
   * @param images generated images
   * @throws Malformation.Clash  when two images change the same file
   * @throws Malformation.Nested when a changed file lies beneath another
   */
  static void validate(@NonNull Collection<? extends Image<?>> images) {
    Map<Address, Image<?>> files = new LinkedHashMap<>();
    for (Image<?> image : images) {
      if (files.putIfAbsent(image.address(), image) != null) {
        throw new Malformation.Clash(image.address());
      }
    }
    for (Address file : files.keySet()) {
      for (Optional<Address> parent = file.parent(); parent.isPresent(); parent = parent.get().parent()) {
        if (files.containsKey(parent.get())) {
          throw new Malformation.Nested(parent.get(), file);
        }
      }
    }
  }

  /**
   * Describes the change that undoes a set. The preimages of a valid set are
   * valid: they change the same files.
   *
   * @param images applied images
   * @return immutable set of preimages
   */
  static Set<Image<?>> preimage(@NonNull Collection<? extends Image<?>> images) {
    return images.stream().map(Image::preimage).collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Composes two changes of one file applied one after the other. A change
   * followed by its exact preimage cancels. Whole files compose into one change
   * from the first's before state to the second's after state. Text changes
   * compose through {@link Chunk#compose(List, List)}: separate regions are
   * kept, and overlapping regions are combined where both describe the same
   * intermediate lines.
   *
   * <p>
   * An installation and a configuration of the same file fold into one
   * installation, which is the only composition that reads content: an
   * installation followed by a configuration installs the content with the
   * chunks applied, and a configuration followed by an installation or
   * deletion starts from the second's before content with the chunks inverted.
   * The stash supplies the retained text and keeps the folded content.
   * Whole-file states continue from one another when they are
   * {@link Alteration#same the same}.
   *
   * @param first  earlier change
   * @param second later change, whose before state must continue from the
   *               first's after state
   * @param stash  stash holding the files' content, read only when folding
   * @return combined change, or empty when the pair has no net effect
   * @throws Malformation.Unrelated     when the changes are of different files
   * @throws Malformation.Discontinuous when the second does not continue from
   *                                    the first
   * @throws IOException                when folded content cannot be read or
   *                                    captured
   */
  static Optional<Image<?>> compose(@NonNull Image<?> first, @NonNull Image<?> second, @NonNull Stash stash)
      throws IOException {
    if (!first.address().equals(second.address())) {
      throw new Malformation.Unrelated(first.address(), second.address());
    }
    return switch (first) {
      case Installation earlier -> switch (second) {
        case Installation later -> earlier.compose(later);
        case Configuration later -> earlier.fold(later, stash);
      };
      case Configuration earlier -> switch (second) {
        case Installation later -> earlier.fold(later, stash);
        case Configuration later -> earlier.compose(later);
      };
    };
  }

  /**
   * Folds sequentially applied sets into the minimal set with the same net
   * effect, which can be exposed in parallel. Files whose changes cancel out
   * disappear.
   *
   * @param changes sets in application order, each individually valid
   * @param stash   stash holding the files' content, read only when an
   *                installation and a configuration of one file fold
   * @return immutable valid net change
   * @throws Malformation       when a set is invalid, its changes are
   *                            discontinuous, or the net change is order
   *                            dependent, such as a file replaced by a
   *                            directory of the same name
   * @throws IOException        when folded content cannot be read or captured
   */
  static Set<Image<?>> reduce(@NonNull List<? extends Collection<? extends Image<?>>> changes,
      @NonNull Stash stash) throws IOException {
    changes.forEach(Image::validate);
    Folding folding = new Folding();
    for (Collection<? extends Image<?>> change : changes) {
      for (Image<?> image : change) {
        folding.fold(image, stash);
      }
    }
    validate(folding.net().values());
    return Set.copyOf(folding.net().values());
  }

  /**
   * Describes the underlying state a change expects or produces: a file's
   * existence and content, or selected lines of a text file.
   */
  sealed interface Alteration {

    /**
     * Whether this state is the same as another. Whole-file states are the
     * same when their content's digest and size match, whatever name it was
     * retained under; other states are the same when they are equal.
     *
     * @param other state to compare
     * @return whether both describe the same state
     */
    default boolean same(@NonNull Alteration other) {
      return equals(other);
    }
  }

  /**
   * Complete file-content expectations, distinguished from absence.
   */
  sealed interface File extends Alteration {

    /**
     * Returns the state of the file a lease holds.
     *
     * @param lease lease on the file
     * @return the file's content, or absence when nothing is at its path
     * @throws Refusal     with {@link Refusal.Occupied} when something other
     *                     than a file is at the path
     * @throws IOException when the file cannot be read
     */
    static File of(@NonNull Lease.Read lease) throws IOException {
      try {
        return new Present(lease.read().identify());
      } catch (Refusal refusal) {
        if (refusal.reason() instanceof Refusal.Missing) {
          return new Absent();
        }
        throw refusal;
      }
    }

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

      @Override
      public boolean same(@NonNull Alteration other) {
        return other instanceof Present(Reference.Captured content)
            && reference.sha256().equals(content.sha256())
            && reference.size() == content.size();
      }
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
   * Creates, deletes, or replaces a whole file. Both sides may be present, but
   * they may not be the same, so both sides are never absent.
   *
   * <p>
   * Exposing it takes a mutual lease on the file and digests it without
   * retaining it. When the file's absence, or its digest and size, match the
   * before state, it deletes the file or places the after content, reopened
   * from the plate's stash and verified as it is written, and passes with the
   * after state. A path no file can occupy, or one that may not be written,
   * fails with the plate's refusal.
   *
   * @param before   complete content or absence expected before the change
   * @param after    complete content or absence described after the change
   * @param address  changed file, beneath the plate's root
   */
  record Installation(
      @NonNull File before, @NonNull File after, @NonNull Address address)
      implements Image<Image.File> {

    /**
     * Rejects the root itself as a file, and a change that changes nothing.
     *
     * @throws Malformation.Root      when the address is the root itself
     * @throws Malformation.Unchanged when both states are the same, including
     *                                both absent
     */
    public Installation {
      if (address.isRoot()) {
        throw new Malformation.Root(address);
      }
      if (before.same(after)) {
        throw new Malformation.Unchanged(address);
      }
    }

    @Override
    public Installation preimage() {
      return new Installation(after, before, address);
    }

    @Override
    public Scrutiny<File> expose(@NonNull Plate plate) throws IOException {
      try {
        Optional<Node> held = held(plate);
        if (held.isEmpty()) {
          return created(plate);
        }
        return switch (held.get()) {
          case Node.File file -> changed(file, plate);
          case Node.Directory _ -> new Scrutiny.Fail<>(null,
              new Refusal(new Refusal.Occupied(address, Refusal.Kind.FILE, Refusal.Kind.DIRECTORY)));
        };
      } catch (Violation violation) {
        // A path beneath a file, or a change the tree does not permit, has no
        // file state to report
        return new Scrutiny.Fail<>(null, violation);
      } catch (Refusal refusal) {
        // A path no file can occupy, or one that may not be written, has no
        // file state to report
        return switch (refusal.reason()) {
          case Refusal.Mismatch _, Refusal.Denied _ -> new Scrutiny.Fail<>(null, refusal);
          case Refusal.Held _ -> throw refusal;
        };
      }
    }

    /**
     * Locates the node the plate's tree holds at this image's file, or empty
     * when it holds nothing there.
     */
    private Optional<Node> held(Plate plate) {
      try {
        return Optional.of(plate.root().locate(address));
      } catch (Violation.Missing _) {
        return Optional.empty();
      }
    }

    /**
     * Creates this image's file where the tree holds nothing, when nothing is
     * what it expects there.
     */
    private Scrutiny<File> created(Plate plate) throws IOException {
      File found = new File.Absent();
      if (!before.same(found) || !(after instanceof File.Present(Reference.Captured content))) {
        return new Scrutiny.Fail<>(found);
      }
      try (Lease.Create file = plate.root().create(address)) {
        file.create(plate.retained(content));
      }
      return new Scrutiny.Pass<>(after);
    }

    /**
     * Brings a file the tree holds from its before state to its after state;
     * a declared file not on disk yet is in the absent state.
     */
    private Scrutiny<File> changed(Node.File node, Plate plate) throws IOException {
      try (Lease.Mutual file = node.mutual()) {
        File found = File.of(file);
        if (!before.same(found)) {
          return new Scrutiny.Fail<>(found);
        }
        switch (after) {
          case File.Absent _ -> file.delete();
          case File.Present(Reference.Captured content) -> file.replace(plate.retained(content));
        }
        return new Scrutiny.Pass<>(after);
      }
    }

    /**
     * Describes the change between two states of a file, unless there is none.
     *
     * @param before   state before
     * @param after    state after
     * @param address  changed file, beneath the plate's root
     * @return the installation, or empty when the states are the same
     */
    static Optional<Image<?>> between(@NonNull File before, @NonNull File after, @NonNull Address address) {
      return before.same(after)
          ? Optional.empty()
          : Optional.of(new Installation(before, after, address));
    }

    /**
     * Composes a later installation of the same file into one change.
     *
     * @param later later installation, whose before state must be the same as
     *              this one's after state
     * @return combined installation, or empty when the later one undoes this
     * @throws Malformation.Discontinuous when the states do not continue
     */
    Optional<Image<?>> compose(@NonNull Installation later) {
      if (!after.same(later.before())) {
        throw new Malformation.Discontinuous(address);
      }
      return between(before, later.after(), address);
    }

    /**
     * Folds a later configuration of the installed file into one installation
     * of the configured content, read from and captured into a stash.
     *
     * @param later configuration of the installed file
     * @param stash stash holding the installed content
     * @return folded installation, or empty when it changes nothing
     * @throws Malformation.Discontinuous when this installation leaves no file,
     *                                    or the chunks do not match its text
     * @throws IOException                when the content cannot be read or
     *                                    captured
     */
    Optional<Image<?>> fold(@NonNull Configuration later, @NonNull Stash stash) throws IOException {
      if (!(after instanceof File.Present(Reference.Captured installed))) {
        throw new Malformation.Discontinuous(address);
      }
      String configured = later.apply(stash.text(installed));
      Reference.Captured content = stash.capture(Describe.of(installed.fileName(), configured));
      return between(before, new File.Present(content), address);
    }
  }

  /**
   * Changes selected ranges of an existing text file, stored as corresponding
   * chunks, at least one of which edits the text. A configuration never
   * creates or deletes a file; an installation does.
   *
   * <p>
   * Exposing it takes a mutual lease on the file and reads it as UTF-8 text.
   * When each chunk's before fragment matches the file's lines at its range,
   * exact endings included, it writes the file with the chunks applied and
   * passes with the file's complete new text; lines outside every chunk are not
   * compared. A failure carries the file's complete current text, or the
   * plate's refusal when the file is gone or cannot be written.
   *
   * @param chunks   text changes
   * @param address  changed file, beneath the plate's root
   */
  record Configuration(
      @NonNull List<Chunk> chunks, @NonNull Address address)
      implements Image<Image.Text> {

    /**
     * Copies and validates the chunks, and rejects the root itself as a file
     * and chunks that change nothing.
     *
     * @throws IllegalArgumentException when the chunks are out of order or
     *                                  overlap
     * @throws Malformation.Root        when the address is the root itself
     * @throws Malformation.Unchanged   when no chunk edits its text
     */
    public Configuration {
      chunks = List.copyOf(chunks);
      Chunk.validateChunks(chunks);
      if (address.isRoot()) {
        throw new Malformation.Root(address);
      }
      if (chunks.stream().noneMatch(Chunk::edits)) {
        throw new Malformation.Unchanged(address);
      }
    }

    /**
     * Describes the change between two fragment states of an existing file.
     *
     * @param before   text before
     * @param after    text after
     * @param address  changed file, beneath the plate's root
     * @throws Malformation.Root      when the address is the root itself
     * @throws Malformation.Unchanged when no fragment changes
     */
    public Configuration(
        @NonNull Text before, @NonNull Text after, @NonNull Address address) {

      this(Chunk.between(before.fragments(), after.fragments()), address);
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
          address);
    }

    @Override
    public Scrutiny<Text> expose(@NonNull Plate plate) throws IOException {
      try {
        return switch (plate.root().locate(address)) {
          case Node.File file -> configured(file);
          case Node.Directory _ -> new Scrutiny.Fail<>(null,
              new Refusal(new Refusal.Occupied(address, Refusal.Kind.FILE, Refusal.Kind.DIRECTORY)));
        };
      } catch (Violation violation) {
        // The tree holds nothing here, the path lies beneath a file, or the
        // tree does not permit the change
        return new Scrutiny.Fail<>(null, violation);
      } catch (Refusal refusal) {
        // The file is gone, is not a file, or may not be written
        return switch (refusal.reason()) {
          case Refusal.Mismatch _, Refusal.Denied _ -> new Scrutiny.Fail<>(null, refusal);
          case Refusal.Held _ -> throw refusal;
        };
      }
    }

    /**
     * Applies the chunks to a file the tree holds, when its lines still match
     * their before fragments.
     */
    private Scrutiny<Text> configured(Node.File node) throws IOException {
      try (Lease.Mutual file = node.mutual()) {
        Describe current = file.read();
        String text = current.text();
        if (!Chunk.matches(chunks, text)) {
          return new Scrutiny.Fail<>(new Text(Chunk.fragmentsOf(text)));
        }
        String changed = Chunk.apply(chunks, text);
        file.update(_ -> Describe.of(current.of().fileName(), changed));
        return new Scrutiny.Pass<>(new Text(Chunk.fragmentsOf(changed)));
      }
    }

    /**
     * Describes chunks of a file as a change, unless none of them edits.
     *
     * @param chunks   text changes
     * @param address  changed file, beneath the plate's root
     * @return the configuration, or empty when no chunk edits its text
     */
    static Optional<Image<?>> between(@NonNull List<Chunk> chunks, @NonNull Address address) {
      return chunks.stream().anyMatch(Chunk::edits)
          ? Optional.of(new Configuration(chunks, address))
          : Optional.empty();
    }

    /**
     * Applies this configuration's chunks to its file's complete text, as
     * folding a later change of the file does.
     *
     * @param text complete text the chunks must match
     * @return complete resulting text
     * @throws Malformation.Discontinuous when the chunks do not match the text
     */
    String apply(@NonNull String text) {
      if (!Chunk.matches(chunks, text)) {
        throw new Malformation.Discontinuous(address);
      }
      return Chunk.apply(chunks, text);
    }

    /**
     * Composes a later configuration of the same file into one change.
     *
     * @param later later configuration
     * @return combined configuration, or empty when the pair has no net effect
     * @throws Malformation.Discontinuous when overlapping regions disagree about
     *                                    the text between them
     */
    Optional<Image<?>> compose(@NonNull Configuration later) {
      List<Chunk> composed;
      try {
        composed = Chunk.compose(chunks, later.chunks());
      } catch (IllegalArgumentException exception) {
        throw new Malformation.Discontinuous(address, exception);
      }
      return between(composed, address);
    }

    /**
     * Folds a later installation or deletion of the configured file into one
     * installation from the content before this configuration, read from and
     * captured into a stash.
     *
     * @param later installation replacing or deleting the configured file
     * @param stash stash holding the configured content
     * @return folded installation, or empty when it changes nothing
     * @throws Malformation.Discontinuous when the later installation expects no
     *                                    file, or the chunks do not match its
     *                                    before text
     * @throws IOException                when the content cannot be read or
     *                                    captured
     */
    Optional<Image<?>> fold(@NonNull Installation later, @NonNull Stash stash) throws IOException {
      if (!(later.before() instanceof File.Present(Reference.Captured configured))) {
        throw new Malformation.Discontinuous(address);
      }
      String original = preimage().apply(stash.text(configured));
      Reference.Captured content = stash.capture(Describe.of(configured.fileName(), original));
      return Installation.between(new File.Present(content), later.after(), address);
    }
  }
}

/**
 * Mutable state of one {@link Image#reduce(List, Stash) reduction}.
 *
 * @param net     net change by file
 * @param settled state a file returned to when its changes cancelled: a later
 *                installation of it must continue from that state
 */
record Folding(Map<Address, Image<?>> net, Map<Address, Image.Alteration> settled) {

  /**
   * Starts an empty reduction.
   */
  Folding() {
    this(new LinkedHashMap<>(), new HashMap<>());
  }

  /**
   * Folds one more change into the net change.
   *
   * @param image later change
   * @param stash stash holding the files' content, for folding
   * @throws Malformation.Discontinuous when the change does not continue from
   *                                    the state its file is in
   * @throws IOException                when folded content cannot be read or
   *                                    captured
   */
  void fold(Image<?> image, Stash stash) throws IOException {
    Address address = image.address();
    Image<?> previous = net.remove(address);
    if (previous == null) {
      Image.Alteration state = settled.remove(address);
      if (state instanceof Image.File file && image instanceof Image.Installation installation
          && !file.same(installation.before())) {
        throw new Malformation.Discontinuous(address);
      }
      net.put(address, image);
      return;
    }
    Image.compose(previous, image, stash).ifPresentOrElse(
        composed -> net.put(address, composed),
        () -> settled.put(address, previous.before()));
  }
}
