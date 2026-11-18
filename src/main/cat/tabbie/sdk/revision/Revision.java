package cat.tabbie.sdk.revision;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cat.tabbie.sdk.failure.Refusal;
import cat.tabbie.sdk.form.Text;
import cat.tabbie.sdk.revision.Hunk.Change;
import cat.tabbie.sdk.revision.Hunk.Fragment;
import cat.tabbie.sdk.revision.Hunk.Range;
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
   * @throws Refusal     with a {@link cat.tabbie.sdk.failure.Reason.Content}
   *                     when the content cannot be read as this revision reads
   *                     it
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
          List<Hunk> composed = earlier.composeWith(next);
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
    public static Optional<Revision<?>> between(
        @NonNull Image.File.Present before,
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
   * <p>
   * A diff finds its hunks between two texts, checks whether they match a
   * text, applies them to it, and composes with a later diff of the same file.
   * Finding them is a deterministic shortest insertion and deletion script,
   * Myers' search over the lines, grouped with unchanged context.
   *
   * @param hunks   hunks in file order, at least one changing a line
   * @param charset charset the file's text is encoded in
   */
  record Diff(@NonNull List<Hunk> hunks, @NonNull Charset charset) implements Revision<Image.Text> {

    /**
     * Matches one line and its ending: LF, CRLF, CR, or none at the end.
     */
    private static final Pattern LINE = Pattern.compile("([^\\r\\n]*)(\\r\\n|\\r|\\n|\\z)");

    /**
     * Unchanged lines kept around each edit when none are asked for.
     */
    private static final int CONTEXT_LINES = 3;

    /**
     * Copies the hunks, and checks that one changes a line, that each side
     * reads as one text, and that both sides leave the same lines unchanged
     * between hunks.
     *
     * @throws IllegalArgumentException when no hunk changes a line, a side's
     *                                  hunks overlap or follow an unterminated
     *                                  line, or the unchanged gaps differ
     */
    public Diff {
      hunks = List.copyOf(hunks);
      if (hunks.stream().noneMatch(Hunk::edits)) {
        throw new IllegalArgumentException("A diff changes a line.");
      }
      List<Fragment> before = side(hunks, Hunk::before).fragments();
      List<Fragment> after = side(hunks, Hunk::after).fragments();
      int beforeEnd = 0;
      int afterEnd = 0;
      for (int index = 0; index < hunks.size(); index++) {
        Range beforeRange = before.get(index).range();
        Range afterRange = after.get(index).range();
        if (beforeRange.start() - beforeEnd != afterRange.start() - afterEnd) {
          throw new IllegalArgumentException("A diff leaves the same lines unchanged between hunks on both sides.");
        }
        beforeEnd = beforeRange.end();
        afterEnd = afterRange.end();
      }
    }

    /**
     * Changes lines of a UTF-8 text file.
     *
     * @param hunks hunks in file order, at least one changing a line
     * @throws IllegalArgumentException as the canonical constructor throws
     */
    public Diff(@NonNull List<Hunk> hunks) {
      this(hunks, StandardCharsets.UTF_8);
    }

    /**
     * Finds the diff between two UTF-8 texts, keeping three unchanged lines
     * around each edit.
     *
     * @param before the text before
     * @param after  the text after
     * @return the diff, or empty when the texts are equal
     */
    public static Optional<Diff> between(@NonNull String before, @NonNull String after) {
      return between(before, after, CONTEXT_LINES);
    }

    /**
     * Finds the diff between two UTF-8 texts: a deterministic shortest script
     * of insertions and deletions, grouped into hunks with up to
     * {@code contextLines} unchanged lines around each edit. Windows that touch
     * merge, and adjacent edits merge even with no context. Lines are equal
     * only with the same ending. The search keeps every frontier it reaches,
     * so a text rewritten throughout needs memory growing with the square of
     * its edits, with no cutoff.
     *
     * @param before       the text before
     * @param after        the text after
     * @param contextLines unchanged lines to keep around each edit
     * @return the diff, or empty when the texts are equal
     * @throws IllegalArgumentException when the context is negative
     */
    public static Optional<Diff> between(@NonNull String before, @NonNull String after, int contextLines) {
      if (contextLines < 0) {
        throw new IllegalArgumentException("A diff keeps no fewer than zero lines of context.");
      }
      return before.equals(after)
          ? Optional.empty()
          : Optional.of(new Diff(new Script(lines(before), lines(after), contextLines).hunks()));
    }

    @Override
    public Image.Text before() {
      return side(hunks, Hunk::before);
    }

    @Override
    public Image.Text after() {
      return side(hunks, Hunk::after);
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
      return matches(text.content())
          ? Optional.of(text.with(apply(text.content())))
          : Optional.empty();
    }

    /**
     * Whether this diff applies to a complete text: each hunk's lines before
     * are the text's lines at its range, endings included. Lines outside every
     * hunk are not compared.
     *
     * @param text the complete text
     * @return true when every hunk's side before matches the text
     */
    public boolean matches(@NonNull String text) {
      List<Change.Line> lines = lines(text);
      return hunks.stream()
          .map(Hunk::before)
          .allMatch(fragment -> fragment.range().end() <= lines.size()
              && lines.subList(fragment.range().start(), fragment.range().end()).equals(fragment.lines()));
    }

    /**
     * Applies this diff to a complete text: each hunk's lines before are
     * replaced by its lines after, and lines outside every hunk stay.
     *
     * @param text the complete text this diff {@link #matches(String) matches}
     * @return the complete resulting text
     * @throws IllegalArgumentException when the diff does not match the text
     */
    public String apply(@NonNull String text) {
      if (!matches(text)) {
        throw new IllegalArgumentException("A diff applies only to text its hunks' lines before match.");
      }
      return project(lines(text).toArray(Change.Line[]::new), 0, hunks, Hunk::before, Hunk::after).stream()
          .map(Change.Line::exact)
          .collect(Collectors.joining());
    }

    /**
     * Composes this diff's hunks with a later diff's, applied one after the
     * other, into hunks from this one's text before to the later one's text
     * after. Regions are compared on the text between them: this diff's ranges
     * after and the later one's ranges before.
     * <ul>
     * <li>A region only one diff changes is kept, its other range shifted by
     * the other diff's net lines before it.</li>
     * <li>Regions that overlap or touch merge into one hunk spanning both,
     * whose lines between must agree where both describe them; a merged
     * region that no longer edits is left out.</li>
     * <li>Changes that cancel only through lines outside every hunk, such as a
     * block moved away and back, are kept, since those lines are not
     * described.</li>
     * </ul>
     *
     * @throws IllegalArgumentException when the two disagree about the lines
     *                                  between them
     */
    private List<Hunk> composeWith(Diff later) {
      List<Hunk> first = hunks;
      List<Hunk> second = later.hunks();
      List<Hunk> result = new ArrayList<>();
      int earlier = 0;
      int next = 0;
      // Offsets from the lines between to the lines before and to the lines after
      int originalOffset = 0;
      int resultingOffset = 0;
      while (earlier < first.size() || next < second.size()) {
        List<Hunk> firsts = new ArrayList<>();
        List<Hunk> seconds = new ArrayList<>();
        Range seed;
        if (next >= second.size()
            || (earlier < first.size()
                && first.get(earlier).afterRange().start() <= second.get(next).beforeRange().start())) {
          Hunk hunk = first.get(earlier++);
          firsts.add(hunk);
          seed = hunk.afterRange();
        } else {
          Hunk hunk = second.get(next++);
          seconds.add(hunk);
          seed = hunk.beforeRange();
        }
        int start = seed.start();
        int end = seed.end();
        while (true) {
          if (earlier < first.size() && first.get(earlier).afterRange().start() <= end) {
            Hunk hunk = first.get(earlier++);
            firsts.add(hunk);
            end = Math.max(end, hunk.afterRange().end());
          } else if (next < second.size() && second.get(next).beforeRange().start() <= end) {
            Hunk hunk = second.get(next++);
            seconds.add(hunk);
            end = Math.max(end, hunk.beforeRange().end());
          } else {
            break;
          }
        }

        if (seconds.isEmpty()) {
          for (Hunk hunk : firsts) {
            result.add(new Hunk(hunk.changes(), hunk.beforeRange(),
                new Range(hunk.afterRange().start() + resultingOffset, hunk.afterRange().count())));
          }
        } else if (firsts.isEmpty()) {
          for (Hunk hunk : seconds) {
            result.add(new Hunk(hunk.changes(),
                new Range(hunk.beforeRange().start() + originalOffset, hunk.beforeRange().count()),
                hunk.afterRange()));
          }
        } else {
          Hunk merged = merge(firsts, seconds, start, end, originalOffset, resultingOffset);
          if (merged.edits()) {
            result.add(merged);
          }
        }

        for (Hunk hunk : firsts) {
          originalOffset += hunk.beforeRange().count() - hunk.afterRange().count();
        }
        for (Hunk hunk : seconds) {
          resultingOffset += hunk.afterRange().count() - hunk.beforeRange().count();
        }
      }
      return List.copyOf(result);
    }

    /**
     * Merges one connected region of the lines between two diffs, which both
     * describe, into one hunk from the lines before to the lines after.
     */
    private static Hunk merge(
        List<Hunk> firsts,
        List<Hunk> seconds,
        int start,
        int end,
        int originalOffset,
        int resultingOffset) {
      Change.Line[] intermediate = new Change.Line[end - start];
      for (Hunk hunk : firsts) {
        describe(intermediate, start, hunk.after());
      }
      for (Hunk hunk : seconds) {
        describe(intermediate, start, hunk.before());
      }
      List<Change.Line> original = project(intermediate, start, firsts, Hunk::after, Hunk::before);
      List<Change.Line> resulting = project(intermediate, start, seconds, Hunk::before, Hunk::after);
      return hunk(
          new Fragment(original, new Range(start + originalOffset, original.size())),
          new Fragment(resulting, new Range(start + resultingOffset, resulting.size())));
    }

    /**
     * Records one diff's description of the lines between, refusing one that
     * disagrees with lines already described.
     */
    private static void describe(Change.Line[] intermediate, int start, Fragment fragment) {
      for (int index = 0; index < fragment.lines().size(); index++) {
        int position = fragment.range().start() + index - start;
        Change.Line line = fragment.lines().get(index);
        if (intermediate[position] != null && !intermediate[position].equals(line)) {
          throw new IllegalArgumentException("Composed diffs agree about the lines between them.");
        }
        intermediate[position] = line;
      }
    }

    /**
     * Rewrites a region of lines through some hunks: lines those hunks match
     * are replaced by their other side, and lines they do not describe stay.
     */
    private static List<Change.Line> project(
        Change.Line[] intermediate,
        int start,
        List<Hunk> hunks,
        Function<Hunk, Fragment> matched,
        Function<Hunk, Fragment> replacement) {
      List<Change.Line> lines = new ArrayList<>();
      int cursor = start;
      for (Hunk hunk : hunks) {
        Range range = matched.apply(hunk).range();
        for (; cursor < range.start(); cursor++) {
          lines.add(intermediate[cursor - start]);
        }
        lines.addAll(replacement.apply(hunk).lines());
        cursor = range.end();
      }
      for (; cursor < start + intermediate.length; cursor++) {
        lines.add(intermediate[cursor - start]);
      }
      return lines;
    }

    /**
     * Pairs a side before with a side after in one hunk, keeping the complete
     * script, so unchanged lines between edits, or an unchanged pair, become
     * context.
     */
    private static Hunk hunk(Fragment before, Fragment after) {
      return new Hunk(new Script(before.lines(), after.lines(), 0).changes(), before.range(), after.range());
    }

    /**
     * Returns one side of some hunks as text, in file order.
     */
    private static Image.Text side(List<Hunk> hunks, Function<Hunk, Fragment> projection) {
      return new Image.Text(hunks.stream()
          .map(projection)
          .toList());
    }

    /**
     * Splits a text into lines at their exact endings, inventing no empty last
     * line: empty text has no lines, and an unterminated last line ends with
     * {@link Change.Sequence#NONE NONE}.
     */
    private static List<Change.Line> lines(String text) {
      return LINE.matcher(text)
          .results()
          .filter(match -> match.start() != match.end())
          .map(match -> new Change.Line(
              match.group(1),
              switch (match.group(2)) {
                case "\r\n" -> Change.Sequence.CRLF;
                case "\r" -> Change.Sequence.CR;
                case "\n" -> Change.Sequence.LF;
                default -> Change.Sequence.NONE;
              }))
          .toList();
    }

    /**
     * A deterministic shortest script of insertions and deletions between two
     * lists of lines, found by Myers' search, and its edits grouped with up to
     * {@code contextLines} unchanged lines on either side. Windows that touch
     * or overlap merge, and adjacent edits merge even with no context: with
     * three lines of context, six unchanged lines between edits merge, and
     * seven leave separate hunks. Lines are equal only with the same ending.
     * The search takes O((N + M)(D + 1)) time and O(N + M + D squared) space,
     * where D is the number of lines inserted or deleted, with no cutoff and
     * no other algorithm to fall back on.
     *
     * @param before       the lines before
     * @param after        the lines after
     * @param contextLines unchanged lines to keep around each edit
     */
    private record Script(List<Change.Line> before, List<Change.Line> after, int contextLines) {

      /**
       * Groups the complete script into hunks of edits with their context.
       */
      private List<Hunk> hunks() {
        return group(changes());
      }

      /**
       * Returns the complete script: the ends both sides share as context,
       * and the shortest script between them.
       */
      private List<Change> changes() {
        int commonSize = Math.min(before.size(), after.size());
        int prefix = (int) IntStream.range(0, commonSize)
            .takeWhile(index -> before.get(index).equals(after.get(index)))
            .count();
        int suffix = (int) IntStream.range(0, commonSize - prefix)
            .takeWhile(index -> before.get(before.size() - index - 1).equals(after.get(after.size() - index - 1)))
            .count();
        int beforeEnd = before.size() - suffix;
        int afterEnd = after.size() - suffix;
        return Stream.of(
            changes(before.subList(0, prefix), Change.Role.CONTEXT),
            shorten(before.subList(prefix, beforeEnd), after.subList(prefix, afterEnd)).stream(),
            changes(before.subList(beforeEnd, before.size()), Change.Role.CONTEXT))
            .flatMap(stream -> stream)
            .toList();
      }

      /**
       * Finds a shortest script between lines that share no ends, keeping each
       * depth's frontier to trace the path back.
       */
      private static List<Change> shorten(List<Change.Line> before, List<Change.Line> after) {
        if (before.isEmpty()) {
          return changes(after, Change.Role.INSERTION).toList();
        }
        if (after.isEmpty()) {
          return changes(before, Change.Role.DELETION).toList();
        }
        int limit = Math.addExact(before.size(), after.size());
        List<int[]> trace = new ArrayList<>();
        for (int depth = 0; depth <= limit; depth++) {
          int[] frontier = new int[Math.addExact(depth, 1)];
          int[] previous = depth == 0 ? null : trace.get(depth - 1);
          for (int index = 0; index <= depth; index++) {
            int diagonal = (int) (2L * index - depth);
            int x = depth == 0 ? 0 : insertion(previous, depth, index) ? previous[index] : previous[index - 1] + 1;
            int y = x - diagonal;
            while (x < before.size() && y < after.size() && before.get(x).equals(after.get(y))) {
              x++;
              y++;
            }
            frontier[index] = x;
            if (x >= before.size() && y >= after.size()) {
              trace.add(frontier);
              return backtrack(before, after, trace);
            }
          }
          trace.add(frontier);
        }
        throw new IllegalStateException("A script between two lists of lines always exists.");
      }

      /**
       * Whether the path reaches a diagonal by an insertion, chosen the same
       * way while searching and while tracing back.
       */
      private static boolean insertion(int[] previous, int depth, int index) {
        return index == 0 || (index < depth && previous[index - 1] < previous[index]);
      }

      /**
       * Traces the shortest path back from the kept frontiers into a script.
       */
      private static List<Change> backtrack(List<Change.Line> before, List<Change.Line> after, List<int[]> trace) {
        List<Change> result = new ArrayList<>();
        int x = before.size();
        int y = after.size();
        for (int depth = trace.size() - 1; depth > 0; depth--) {
          int diagonal = x - y;
          int index = (int) (((long) diagonal + depth) / 2);
          int[] previous = trace.get(depth - 1);
          boolean inserted = insertion(previous, depth, index);
          int previousDiagonal = inserted ? diagonal + 1 : diagonal - 1;
          int previousX = previous[inserted ? index : index - 1];
          int previousY = previousX - previousDiagonal;
          while (x > previousX && y > previousY) {
            result.add(new Change(before.get(--x), Change.Role.CONTEXT));
            y--;
          }
          result.add(inserted
              ? new Change(after.get(--y), Change.Role.INSERTION)
              : new Change(before.get(--x), Change.Role.DELETION));
        }
        while (x > 0 && y > 0) {
          result.add(new Change(before.get(--x), Change.Role.CONTEXT));
          y--;
        }
        Collections.reverse(result);
        return result;
      }

      /**
       * Groups a script's edits into hunks, each with its context; unchanged
       * lines left out advance both sides alike.
       */
      private List<Hunk> group(List<Change> script) {
        int[] edits = IntStream.range(0, script.size())
            .filter(index -> script.get(index).role() != Change.Role.CONTEXT)
            .toArray();
        List<Hunk> hunks = new ArrayList<>();
        int cursor = 0;
        int beforePosition = 0;
        int afterPosition = 0;
        for (int edit = 0; edit < edits.length;) {
          int start = Math.max(0, edits[edit] - contextLines);
          int end = window(edits[edit++], script.size());
          while (edit < edits.length && (long) edits[edit] - contextLines <= end) {
            end = window(edits[edit++], script.size());
          }
          beforePosition += start - cursor;
          afterPosition += start - cursor;
          int beforeStart = beforePosition;
          int afterStart = afterPosition;
          for (cursor = start; cursor < end; cursor++) {
            Change.Role role = script.get(cursor).role();
            if (role != Change.Role.INSERTION) {
              beforePosition++;
            }
            if (role != Change.Role.DELETION) {
              afterPosition++;
            }
          }
          hunks.add(new Hunk(script.subList(start, end),
              new Range(beforeStart, beforePosition - beforeStart),
              new Range(afterStart, afterPosition - afterStart)));
        }
        return List.copyOf(hunks);
      }

      /**
       * Returns where an edit's context window ends, clamped to the script
       * without overflowing.
       */
      private int window(int edit, int size) {
        return (int) Math.min(size, (long) edit + contextLines + 1);
      }

      /**
       * Gives each line one role.
       */
      private static Stream<Change> changes(List<Change.Line> lines, Change.Role role) {
        return lines.stream().map(line -> new Change(line, role));
      }
    }
  }
}
