package cat.tabbie.sdk.album.revision;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.platform.Relative;
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
 * Images describe the instance, not the machine it runs on. What an instance
 * needs from its machine, such as a system package, is data in the instance's
 * lockfile, which images edit like any other text file.
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
   *         state found, or null when the found state cannot be described
   * @throws IOException when the file cannot be read or the change fails
   *                     part-way
   */
  Scrutiny<State> expose(@NonNull Plate plate) throws IOException;

  /**
   * File this image changes.
   *
   * @return changed file, beneath its mount
   */
  @NonNull
  Relative relative();

  /**
   * Expected existence or content before the change
   *
   * @return expected state before
   */
  @NonNull
  State before();

  /**
   * Described existence or content after the change
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
    return new Installation(new File.Absent(), new File.Present(after), path);
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
    return new Installation(new File.Present(before), new File.Present(after), path);
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
    return new Installation(new File.Present(before), new File.Absent(), path);
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
    return new Configuration(before, after, path);
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
    return new Configuration(chunks, path);
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
   * Checks that images can be exposed in any order, and so in parallel: every
   * file appears once, and no file is also used as a directory under the same
   * mount.
   *
   * @param images generated images
   * @throws IllegalArgumentException when generated images cannot be exposed in
   *                                  any order
   */
  static void validate(@NonNull Collection<? extends Image<?>> images) {
    Map<Relative, Image<?>> files = new LinkedHashMap<>();
    for (Image<?> image : images) {
      Image<?> clash = files.putIfAbsent(image.relative(), image);
      if (clash != null) {
        throw new IllegalArgumentException("Images require unique files: " + clash);
      }
    }
    for (Relative file : files.keySet()) {
      for (Optional<Relative> parent = file.parent(); parent.isPresent(); parent = parent.get().parent()) {
        if (files.containsKey(parent.get())) {
          throw new IllegalArgumentException("A file destination may not also be used as a directory");
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
   * compose
   * through {@link Chunk#compose(List, List)}: separate regions are kept, and
   * overlapping regions are combined where both describe the same intermediate
   * lines.
   *
   * <p>
   * An installation and a configuration of the same file fold into one
   * installation by reading the retained content: an installation followed by
   * a configuration installs the content with the chunks applied, and a
   * configuration followed by an installation or deletion starts from the
   * second's before content with the chunks inverted. The folded content is
   * captured into the retention backend when the result is collapsed.
   * Whole-file states agree when their digest and size match; the file name a
   * capture was retained under is not part of the file's state.
   *
   * @param first      earlier change
   * @param second     later change, whose before state must agree with the
   *                   first's after state
   * @param repository retention backend holding the files' content
   * @return unevaluated combined change, empty when the pair has no net effect
   * @throws IllegalArgumentException when files differ or states are
   *                                  discontinuous; discontinuity found while
   *                                  folding content is thrown on collapse
   */
  static Intermediate<Optional<Image<?>>> compose(
      @NonNull Image<?> first,
      @NonNull Image<?> second,
      @NonNull Extract repository) {

    // Must be the same file
    if (!first.relative().equals(second.relative())) {
      throw new IllegalArgumentException(
          "Only changes of the same file compose: %s and %s".formatted(first.relative(), second.relative()));
    }

    // Files agree, so differing kinds are an installation and a configuration
    // of one file
    if (first instanceof Installation earlier && second instanceof Configuration later) {
      return fold(earlier, later, repository);
    }
    if (first instanceof Configuration earlier && second instanceof Installation later) {
      return fold(earlier, later, repository);
    }
    return Intermediate.of(compose(first, second));
  }

  /**
   * Composes two changes of one kind, which needs no content.
   *
   * @param first  earlier change
   * @param second later change of the same kind and file
   * @return combined change, or empty when the pair has no net effect
   */
  private static Optional<Image<?>> compose(Image<?> first, Image<?> second) {

    // Second has a discontinuity error with first. Text changes describe only
    // selected regions, so Chunk.compose checks their agreement instead.
    if (!(first instanceof Configuration) && !same(first.after(), second.before())) {
      throw new IllegalArgumentException(
          "Changes are discontinuous at %s".formatted(first.relative()));
    }

    // Second is a preimage of first. Text states are partial, so equal
    // fragments prove nothing and Chunk.compose decides instead.
    if (!(first instanceof Configuration) && same(first.before(), second.after())) {
      return Optional.empty();
    }

    return switch (first) {
      // Collapse [P -> A -> P] or [A -> P -> A]
      case Installation earlier ->
        Optional.of(new Installation(earlier.before(), ((Installation) second).after(), earlier.relative()));

      // Collapse [A ⊆ B] or [B ⊆ A]
      case Configuration earlier -> {
        List<Chunk> composed = Chunk.compose(earlier.chunks(), ((Configuration) second).chunks());
        yield composed.isEmpty()
            ? Optional.empty()
            : Optional.of(new Configuration(composed, earlier.relative()));
      }
    };
  }

  /**
   * Folds an installation and a later configuration of its file into one
   * installation of the configured content.
   *
   * @param earlier    installation
   * @param later      configuration of the installed file
   * @param repository retention backend holding the installed content
   * @return unevaluated folded change
   */

  // TODO: Remove Intermediate Wrapping, this should return Optional
  private static Intermediate<Optional<Image<?>>> fold(Installation earlier, Configuration later,
      Extract repository) {
    if (!(earlier.after() instanceof File.Present(Reference.Captured installed))) {
      throw new IllegalArgumentException("Changes are discontinuous at %s".formatted(earlier.relative()));
    }
    return Intermediate.of(ignored -> text(repository, installed))
        .flatMap(text -> repository.capture(new Reference.Pending(installed.fileName()),
            Chunk.applied(later.chunks(), text, earlier.relative())))
        .map(captured -> net(new Installation(earlier.before(), new File.Present(captured), earlier.relative())));
  }

  /**
   * Folds a configuration and a later installation or deletion of its file
   * into one installation from the content before the configuration.
   *
   * @param earlier    configuration
   * @param later      installation replacing or deleting the configured file
   * @param repository retention backend holding the configured content
   * @return unevaluated folded change
   */
  // TODO: Remove Intermediate Wrapping, this should return Optional
  // TODO: Try to see if we can avoid using an extract here, the state already has
  // file references anyways
  private static Intermediate<Optional<Image<?>>> fold(Configuration earlier, Installation later,
      Extract repository) {
    if (!(later.before() instanceof File.Present(Reference.Captured configured))) {
      throw new IllegalArgumentException("Changes are discontinuous at %s".formatted(later.relative()));
    }
    List<Chunk> inverted = earlier.chunks().stream()
        .map(Chunk::invert)
        .toList();
    return Intermediate.of(ignored -> repository.text(configured))
        .flatMap(text -> repository.capture(new Reference.Pending(configured.fileName()),
            Chunk.applied(inverted, text, later.relative())))
        .map(captured -> net(new Installation(new File.Present(captured), later.after(), later.relative())));
  }

  /**
   * Keeps a folded installation unless it changes nothing.
   *
   * @param installation folded installation
   * @return the installation, or empty when its states agree
   */
  private static Optional<Image<?>> net(Installation installation) {
    return same(installation.before(), installation.after())
        ? Optional.empty()
        : Optional.of(installation);
  }

  /**
   * Whole-file states match by content; other states match exactly.
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
    return first.equals(second);
  }

  /**
   * Folds sequentially applied sets into the minimal set with the same net
   * effect, which can be exposed in parallel. Files whose changes cancel out
   * disappear.
   *
   * @param changes    sets in application order, each individually valid
   * @param repository retention backend holding files' content, for folding
   *                   installations and configurations of one file
   * @return unevaluated immutable valid net change
   * @throws IllegalArgumentException when a set is invalid; discontinuous
   *                                  changes, and a net change that is order
   *                                  dependent, such as a file replaced by a
   *                                  directory of the same name, are thrown on
   *                                  collapse
   */
  static Intermediate<Set<Image<?>>> reduce(@NonNull List<? extends Collection<? extends Image<?>>> changes,
      @NonNull Extract repository) {
    changes.forEach(Image::validate);
    // Each collapse folds into fresh state, so collapsing again repeats the fold
    Intermediate<Folding> folding = Intermediate.of(ignored -> new Folding(new LinkedHashMap<>(), new HashMap<>()));
    for (Collection<? extends Image<?>> change : changes) {
      for (Image<?> image : change) {
        folding = folding.flatMap(state -> fold(state, image, repository));
      }
    }
    return folding.map(state -> {
      validate(state.net().values());
      return Set.copyOf(state.net().values());
    });
  }

  /**
   * Folds one more change into a reduction's net change.
   *
   * @param state      reduction state, owned by a single collapse
   * @param image      later change
   * @param repository retention backend for folding content
   * @return unevaluated state after the change
   */
  private static Intermediate<Folding> fold(Folding state, Image<?> image, Extract repository) {
    Relative relative = image.relative();
    Image<?> previous = state.net().remove(relative);
    if (previous == null) {
      Image<?> settled = state.cancelled().remove(relative);
      if (settled != null && settled.getClass() == image.getClass() && !(image instanceof Configuration)
          && !same(settled.before(), image.before())) {
        throw new IllegalArgumentException("Changes are discontinuous at %s".formatted(relative));
      }
      state.net().put(relative, image);
      return Intermediate.of(state);
    }
    return compose(previous, image, repository).map(composed -> {
      if (composed.isPresent()) {
        state.net().put(relative, composed.get());
      } else {
        state.cancelled().put(relative, previous);
      }
      return state;
    });
  }

  /**
   * Describes the underlying state a change expects or produces: a file's
   * existence and content, or selected lines of a text file.
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
   * Creates, deletes, or replaces a whole file. Both sides may be present, but
   * both sides may not be absent. Equal references describe an unchanged file.
   *
   * <p>
   * Exposing it write-leases the file and digests it without retaining it.
   * When the file's absence, or its digest and size, match the before state, it
   * deletes the file or places the after content, read from the plate's
   * retention backend and verified as it is written, and passes with the after
   * state. A path no file can occupy, or one that may not be written, fails
   * without a state.
   *
   * @param before   complete content or absence expected before the change
   * @param after    complete content or absence described after the change
   * @param relative changed file, beneath its mount
   */
  record Installation(
      @NonNull File before, @NonNull File after, @NonNull Relative relative)
      implements Image<Image.File> {

    /**
     * Rejects an absence-to-absence description and the mount itself as a file.
     *
     * @throws IllegalArgumentException when both sides are absent, or the path
     *                                  is its mount
     */
    public Installation {
      if (before instanceof File.Absent && after instanceof File.Absent) {
        throw new IllegalArgumentException("Files may not both be absent!");
      }
      if (relative.isMount()) {
        throw new IllegalArgumentException("A changed file must lie beneath its mount.");
      }
    }

    @Override
    public Installation preimage() {
      return new Installation(after, before, relative);
    }

    @Override
    public Scrutiny<File> expose(@NonNull Plate plate) throws IOException {
      try (Plate.Lease.Write file = plate.write(relative)) {
        Optional<Describe> current = file.content();
        File found = current.isPresent()
            ? new File.Present(current.get().identify())
            : new File.Absent();
        if (!same(before, found)) {
          return new Scrutiny.Fail<>(found);
        }
        switch (after) {
          case File.Absent _ -> file.delete();
          case File.Present(Reference.Captured content) when found instanceof File.Absent ->
            file.create(plate.repository().retained(content));
          case File.Present(Reference.Captured content) -> file.update(plate.repository().retained(content));
        }
        return new Scrutiny.Pass<>(after);
      } catch (Refusal refusal) {
        // A path no file can occupy, or one that may not be written, has no
        // file state to report
        return switch (refusal.reason()) {
          case Refusal.Mismatch _,Refusal.Denied _ -> new Scrutiny.Fail<>();
          case Refusal.Held _ -> throw refusal;
        };
      }
    }
  }

  /**
   * Changes selected ranges of an existing text file, stored as corresponding
   * chunks. A configuration never creates or deletes a file; an installation
   * does.
   *
   * <p>
   * Exposing it write-leases the file and reads it as UTF-8 text. When each
   * chunk's
   * before fragment matches the file's lines at its range, exact endings
   * included, it writes the file with the chunks applied and passes with the
   * file's complete new text; lines outside every chunk are not compared. A
   * failure carries the file's complete current text, or nothing when the file
   * is gone or cannot be written.
   *
   * @param chunks   text changes
   * @param relative changed file, beneath its mount
   */
  record Configuration(
      @NonNull List<Chunk> chunks, @NonNull Relative relative)
      implements Image<Image.Text> {

    /**
     * Copies and validates the chunks, and rejects the mount itself as a file.
     *
     * @throws IllegalArgumentException when the chunks are invalid, or the path
     *                                  is its mount
     */
    public Configuration {
      chunks = List.copyOf(chunks);
      Chunk.validateChunks(chunks);
      if (relative.isMount()) {
        throw new IllegalArgumentException("A changed file must lie beneath its mount.");
      }
    }

    /**
     * Describes the change between two fragment states of an existing file.
     *
     * @param before   text before
     * @param after    text after
     * @param relative changed file, beneath its mount
     */
    public Configuration(
        @NonNull Text before, @NonNull Text after, @NonNull Relative relative) {

      this(Chunk.between(before.fragments(), after.fragments()), relative);
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
          relative);
    }

    @Override
    public Scrutiny<Text> expose(@NonNull Plate plate) throws IOException {
      try (Plate.Lease.Write file = plate.write(relative)) {
        Optional<Describe> current = file.content();
        if (current.isEmpty()) {
          return new Scrutiny.Fail<>();
        }
        String text = current.get().text();
        if (!Chunk.matches(chunks, text)) {
          return new Scrutiny.Fail<>(new Text(Chunk.fragmentsOf(text)));
        }
        String changed = Chunk.apply(chunks, text);
        file.update(Describe.text(current.get().of().fileName(), changed));
        return new Scrutiny.Pass<>(new Text(Chunk.fragmentsOf(changed)));
      } catch (Refusal refusal) {
        // The file is gone, is not a file, or may not be written
        return switch (refusal.reason()) {
          case Refusal.Mismatch _,Refusal.Denied _ -> new Scrutiny.Fail<>();
          case Refusal.Held _ -> throw refusal;
        };
      }
    }
  }

}

/**
 * Mutable state of one {@link Image#reduce(List, Extract) reduction}, owned by
 * a single collapse.
 *
 * @param net       net change by file
 * @param cancelled earlier halves of cancelled pairs: a later change of the
 *                  same kind must continue from the state its file returned
 *                  to
 */
record Folding(Map<Relative, Image<?>> net, Map<Relative, Image<?>> cancelled) {
}