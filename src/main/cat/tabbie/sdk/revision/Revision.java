package cat.tabbie.sdk.revision;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import cat.tabbie.sdk.failure.Refusal;
import cat.tabbie.sdk.form.Text;
import cat.tabbie.sdk.tree.Shape;
import lombok.NonNull;

/**
 * How a {@link Image.Configuration configuration} changes the content of a
 * file that exists: by {@link Swap swapping} its whole content, or by a
 * {@link Diff diff} of positioned lines. Revisions are split by how they
 * change bytes, never by format: a form that keeps a structured file
 * consistent describes its changes as one of these. A revision for large
 * files, such as World data, joins them later.
 *
 * <p>
 * Each revision has a side before and a side after, typed by its kind: a swap
 * sees the whole file {@link Image.File.Present present} on both sides, and a
 * diff sees the {@link Image.Text text} of the lines it touches. A revision
 * always changes something: one whose sides would be the same is refused when
 * it is made, and composing two that cancel out gives none.
 *
 * @param <Side> what each side of the revision is
 */
public sealed interface Revision<Side extends Image.Alteration> permits Revision.Swap, Revision.Diff {

  /**
   * Returns what the revision changes from.
   *
   * @return the side before
   */
  @NonNull
  Side before();

  /**
   * Returns what the revision changes to.
   *
   * @return the side after
   */
  @NonNull
  Side after();

  /**
   * Returns the revision that undoes this one, exchanging its sides.
   *
   * @return the inverse revision
   */
  @NonNull
  Revision<Side> invert();

  /**
   * Revises a file's content, when it is the content this revision changes
   * from.
   *
   * @param current the file as it is
   * @return the file's revised content, or empty when its content is not what
   *         this revision changes from
   * @throws Refusal     with a {@link cat.tabbie.sdk.failure.Reason.Content} when
   *                     the content cannot be read as this revision reads it
   * @throws IOException when the file cannot be read
   */
  @NonNull
  Optional<Shape.File> revise(@NonNull Shape.File current) throws IOException;

  /**
   * Composes this revision with a later one of the same file into one
   * revision from this one's side before to the later one's side after. Two
   * diffs compose by their lines; a swap with a diff composes into a swap of
   * the revised content, held in memory.
   *
   * @param later the revision applied after this one
   * @return the composed revision, or empty when the two cancel out
   * @throws IllegalArgumentException when the later revision does not
   *                                  continue from this one's side after
   * @throws IOException              when content cannot be read
   */
  @NonNull
  default Optional<Revision<?>> compose(@NonNull Revision<?> later) throws IOException {
    return switch (this) {
      case Swap earlier -> switch (later) {
        case Swap next -> {
          if (!earlier.after().same(next.before())) {
            throw new IllegalArgumentException("A later swap continues from the earlier swap's content.");
          }
          yield Swap.between(earlier.before(), next.after());
        }
        case Diff next -> Swap.between(earlier.before(), new Image.File.Present(next.revise(earlier.after().content()
            .shape())
            .orElseThrow(() -> new IllegalArgumentException("A later diff continues from the earlier swap's content."))
            .describe()));
      };
      case Diff earlier -> switch (later) {
        case Swap next -> Swap.between(new Image.File.Present(earlier.invert().revise(next.before().content().shape())
            .orElseThrow(() -> new IllegalArgumentException("A later swap continues from the earlier diff's result."))
            .describe()), next.after());
        case Diff next -> {
          if (!earlier.charset().equals(next.charset())) {
            throw new IllegalArgumentException("Diffs compose only in one charset.");
          }
          List<Hunk> composed = Hunk.compose(earlier.hunks(), next.hunks());
          yield composed.stream().anyMatch(Hunk::edits)
              ? Optional.of(new Diff(composed, earlier.charset()))
              : Optional.empty();
        }
      };
    };
  }

  /**
   * Replaces a file's whole content: the file is present on both sides, each
   * side's description bound to the bytes it holds.
   *
   * @param before the file as it is before
   * @param after  the file as it is after
   */
  record Swap(@NonNull Image.File.Present before, @NonNull Image.File.Present after)
      implements Revision<Image.File.Present> {

    /**
     * Checks that the swap changes the content.
     *
     * @throws IllegalArgumentException when both sides hold the same bytes
     */
    public Swap {
      if (before.same(after)) {
        throw new IllegalArgumentException("A swap changes the file's content.");
      }
    }

    /**
     * Swaps one content for another, unless they are the same.
     *
     * @param before the file as it is before
     * @param after  the file as it is after
     * @return the swap, or empty when both sides hold the same bytes
     */
    public static Optional<Revision<?>> between(@NonNull Image.File.Present before,
        @NonNull Image.File.Present after) {
      return before.same(after)
          ? Optional.empty()
          : Optional.of(new Swap(before, after));
    }

    @Override
    public Swap invert() {
      return new Swap(after, before);
    }

    @Override
    public Optional<Shape.File> revise(@NonNull Shape.File current) throws IOException {
      return current.describe().agrees(before.content())
          ? Optional.of(after.content().shape())
          : Optional.empty();
    }
  }

  /**
   * Changes lines of a text file, as positioned {@link Hunk hunks} read
   * through the file's {@link Text} in a charset. Each side is the text of the
   * lines the hunks touch.
   *
   * @param hunks   hunks in file order, at least one changing a line
   * @param charset charset the file's text is encoded in
   */
  record Diff(@NonNull List<Hunk> hunks, @NonNull Charset charset) implements Revision<Image.Text> {

    /**
     * Copies the hunks, and checks their order and that one changes a line.
     *
     * @throws IllegalArgumentException when the hunks overlap, their unchanged
     *                                  gaps differ, or none changes a line
     */
    public Diff {
      hunks = List.copyOf(hunks);
      Hunk.validateHunks(hunks);
      if (hunks.stream().noneMatch(Hunk::edits)) {
        throw new IllegalArgumentException("A diff changes a line.");
      }
    }

    /**
     * Changes lines of a UTF-8 text file.
     *
     * @param hunks hunks in file order, at least one changing a line
     * @throws IllegalArgumentException when the hunks overlap, their unchanged
     *                                  gaps differ, or none changes a line
     */
    public Diff(@NonNull List<Hunk> hunks) {
      this(hunks, StandardCharsets.UTF_8);
    }

    @Override
    public Image.Text before() {
      return new Image.Text(
          hunks.stream()
              .map(Hunk::before)
              .toList());
    }

    @Override
    public Image.Text after() {
      return new Image.Text(
          hunks.stream()
              .map(Hunk::after)
              .toList());
    }

    @Override
    public Diff invert() {
      return new Diff(
          hunks.stream()
              .map(Hunk::invert)
              .toList(),
          charset);
    }

    @Override
    public Optional<Shape.File> revise(@NonNull Shape.File current) throws IOException {
      Text text = current.as(Text.in(charset));
      return Hunk.matches(hunks, text.content())
          ? Optional.of(text.with(Hunk.apply(hunks, text.content())))
          : Optional.empty();
    }
  }
}
