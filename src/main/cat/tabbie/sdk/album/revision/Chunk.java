package cat.tabbie.sdk.album.revision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cat.tabbie.sdk.album.revision.Chunk.Change.Line;
import cat.tabbie.sdk.album.revision.Chunk.Change.Revision;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * One contiguous region of a text change, including its unchanged context. Each
 * ordered change consumes one before line unless it is an insertion, and one
 * after line unless it is a deletion. Context consumes a line on both sides.
 * Starting at the two ranges therefore determines every change's positions;
 * the consumed counts must equal the respective range counts.
 *
 * Each side projects to exactly one fragment, including zero-length fragments
 * at insertion/deletion boundaries. Manually authored chunks may be empty or
 * context-only. Across a file, chunks must be ordered and non-overlapping on
 * both sides, with equal lengths of omitted unchanged gaps.
 *
 * Ranges start at zero and end exclusively. Line equality includes the exact
 * ending (LF, CRLF, CR, or none); line text excludes newline characters.
 * An unterminated line can only end its described file side. Collections are
 * immutable defensive copies.
 *
 * @param changes     ordered context and edits
 * @param beforeRange original line range
 * @param afterRange  resulting line range
 */
public record Chunk(@NonNull List<Change> changes, @NonNull Range beforeRange, @NonNull Range afterRange) {

  private static final Pattern LINE_PATTERN = Pattern.compile("([^\\r\\n]*)(\\r\\n|\\r|\\n|\\z)");
  private static final Integer CONTEXT_LINES = 3;

  /**
   * Copies changes and checks consumed counts and terminal lines on both sides.
   *
   * @throws IllegalArgumentException when changes disagree with either range
   */
  public Chunk {
    changes = List.copyOf(changes);
    validateFragment(beforeRange, changes, Revision.INSERTION);
    validateFragment(afterRange, changes, Revision.DELETION);
  }

  /**
   * Contiguous original text, excluding any inserted lines
   *
   * @return original text
   */
  public Fragment before() {
    return fragment(beforeRange, changes, Revision.INSERTION);
  }

  /**
   * Contiguous resulting text, excluding deleted lines
   *
   * @return resulting text
   */
  public Fragment after() {
    return fragment(afterRange, changes, Revision.DELETION);
  }

  /**
   * Swaps the ranges and edit roles, retaining change order and exact lines
   *
   * @return reversed chunk
   */
  public Chunk invert() {
    return new Chunk(
        changes.stream()
            .map(change -> new Change(change.line(), change.revision().invert()))
            .toList(),
        afterRange,
        beforeRange);
  }

  /**
   * Splits exact LF, CRLF, and CR endings without inventing a trailing empty
   * line. Empty text has no lines; a final unterminated line uses {@code NONE}.
   *
   * @param text text
   * @return changes
   */
  public static List<Change.Line> linesOf(@NonNull String text) {
    return LINE_PATTERN.matcher(text)
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
   * Finds a deterministic shortest insertion/deletion script with three unchanged
   * context lines on each side. Touching context windows merge.
   *
   * Exact line endings participate in equality. Trace-based Myers search uses
   * O(N + M + D squared) space; extensive rewrites can consume substantial
   * memory, with no arbitrary cutoff or fallback.
   *
   * @param before original complete text
   * @param after  resulting complete text
   * @return immutable edited chunks, empty for identical input
   */
  public static List<Chunk> diff(@NonNull String before, @NonNull String after) {
    return diff(before, after, CONTEXT_LINES);
  }

  /**
   * Finds a deterministic shortest insertion/deletion script with the requested
   * unchanged context on each side. Touching context windows merge. Adjacent
   * edits merge even at zero
   * context.
   *
   * Exact line endings participate in equality. Trace-based Myers search uses
   * O(N + M + D squared) space; extensive rewrites can consume substantial
   * memory, with no arbitrary cutoff or fallback.
   *
   * @param before       original complete text
   * @param after        resulting complete text
   * @param contextLines nonnegative surrounding unchanged line count
   * @return immutable ordered edited chunks; empty if unchanged
   * @throws IllegalArgumentException when context is negative
   */
  public static List<Chunk> diff(@NonNull String before, @NonNull String after, int contextLines) {
    if (contextLines < 0) {
      throw new IllegalArgumentException("Context line count must be non-negative.");
    }
    return before.equals(after) ? List.of() : new Diff(linesOf(before), linesOf(after), contextLines).chunks();
  }

  /**
   * Checks one file side for ordered, non-overlapping ranges and valid
   * termination.
   * Empty boundaries at an unterminated end are allowed; later text or gaps are
   * not.
   *
   * @param fragments fragments in file order
   * @throws IllegalArgumentException when ranges overlap, regress, or follow an
   *                                  unterminated line
   */
  public static void validateFragments(@NonNull List<Chunk.Fragment> fragments) {
    int end = 0;
    boolean unterminated = false;
    for (Chunk.Fragment fragment : fragments) {
      if (fragment.range().start() < end) {
        throw new IllegalArgumentException("Text fragments must be ordered and non-overlapping.");
      }
      if (unterminated && (fragment.range().start() > end || !fragment.lines().isEmpty())) {
        throw new IllegalArgumentException("Text cannot follow an unterminated final line.");
      }
      end = fragment.range().end();
      if (!fragment.lines().isEmpty()) {
        unterminated = fragment.lines().getLast().ending() == Change.Sequence.NONE;
      }
    }
  }

  /**
   * Checks one file side for ordered, non-overlapping ranges and valid
   * termination.
   * Empty boundaries at an unterminated end are allowed; later text or gaps are
   * not.
   *
   * @param before fragments before in file order
   * @param after  fragments after in file order
   * @throws IllegalArgumentException when ranges overlap, regress, or follow an
   *                                  unterminated line
   */
  public static void validateFragments(@NonNull List<Fragment> before, @NonNull List<Fragment> after) {
    validateFragments(before);
    validateFragments(after);
    if (before.size() != after.size()) {
      throw new IllegalArgumentException("Text fragments must correspond by index.");
    }
    int beforeEnd = 0;
    int afterEnd = 0;
    for (int index = 0; index < before.size(); index++) {
      Range beforeRange = before.get(index).range();
      Range afterRange = after.get(index).range();
      int beforeGap = beforeRange.start() - beforeEnd;
      int afterGap = afterRange.start() - afterEnd;
      if (afterGap < 0 || beforeGap != afterGap) {
        throw new IllegalArgumentException("Fragments must be ordered with matching unchanged gaps.");
      }
      beforeEnd = beforeRange.end();
      afterEnd = afterRange.end();
    }
  }

  /**
   * Validates both projected sides of an authored chunk sequence.
   *
   * @param chunks chunks in file order
   * @throws IllegalArgumentException when ranges overlap or unchanged gaps differ
   */
  public static void validateChunks(@NonNull List<Chunk> chunks) {
    validateFragments(
        chunks.stream()
            .map(Chunk::before)
            .toList(),
        chunks.stream()
            .map(Chunk::after)
            .toList());
  }

  /**
   * Validates the lines consumed by one projected side.
   *
   * @param range    that side's declared range
   * @param changes  ordered script
   * @param excluded role that consumes no lines on this side
   */
  private static void validateFragment(@NonNull Range range, @NonNull List<Change> changes,
      @NonNull Change.Revision excluded) {
    Fragment.validate(
        changes.stream()
            .filter(entry -> entry.revision() != excluded)
            .map(Change::line)
            .toList(),
        range);
  }

  /**
   * Projects one side while retaining exact line objects.
   *
   * @param range    that side's declared range
   * @param changes  ordered script
   * @param excluded role to omit
   * @return one contiguous fragment
   */
  private static Fragment fragment(@NonNull Range range, @NonNull List<Change> changes,
      @NonNull Change.Revision excluded) {
    return new Fragment(
        changes.stream()
            .filter(entry -> entry.revision() != excluded)
            .map(Change::line)
            .toList(),
        range);
  }

  /**
   * One exact line and its role. Positions follow from chunk ranges and
   * preceding changes, rather than being duplicated on each change.
   *
   * @param line     exact text and ending
   * @param revision consumption role
   */
  public record Change(@NonNull Line line, @NonNull Revision revision) {

    /**
     * Exact characters terminating a line, including no ending.
     */
    @AllArgsConstructor
    public enum Sequence {

      /**
       * Line feed
       */
      LF("\n"),

      /**
       * Carriage return followed by line feed
       */
      CRLF("\r\n"),

      /**
       * Carriage return
       */
      CR("\r"),

      /**
       * No terminator; this must end the described file side
       */
      NONE("");

      /**
       * Exact terminator characters.
       *
       * @return exact terminator characters, possibly empty
       */
      @Getter
      private final String value;
    }

    /**
     * A line's role within a text change, unrelated to resource revisions.
     */
    public enum Revision {

      /**
       * Consumes no original line and one resulting line
       */
      INSERTION,

      /**
       * Consumes one line on both sides
       */
      CONTEXT,

      /**
       * Consumes one original line and no resulting line
       */
      DELETION;

      /**
       * Exchanges insertion and deletion, preserving context.
       *
       * @return inverse consumption role
       */
      public Revision invert() {
        return switch (this) {
          case CONTEXT -> CONTEXT;
          case DELETION -> INSERTION;
          case INSERTION -> DELETION;
        };
      }
    }

    /**
     * Text excluding newline characters, paired with its exact ending.
     *
     * @param text   line characters without CR or LF
     * @param ending original terminator, or none
     */
    public record Line(@NonNull String text, @NonNull Sequence ending) {
      /**
       * Checks that line content excludes terminators.
       */
      public Line {
        if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
          throw new IllegalArgumentException("Line text must not contain newline characters.");
        }
      }
    }
  }

  /**
   * A zero-based line range with an exclusive end. A zero count describes
   * an insertion/deletion boundary. The end must fit in a nonnegative integer.
   *
   * @param start zero-based first line or boundary
   * @param count consumed line count
   */
  public record Range(int start, int count) {

    /**
     * Checks nonnegative start/count and a representable end position.
     */
    public Range {
      if (start < 0 || count < 0 || (long) start + count > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Line range must have a valid non-negative end.");
      }
    }

    /**
     * Computes the previously validated exclusive end.
     *
     * @return start plus count
     */
    public int end() {
      return start + count;
    }
  }

  /**
   * One contiguous projection of a chunk's file side, including an empty
   * boundary.
   *
   * @param lines exact lines in file order
   * @param range described line range
   */
  public record Fragment(@NonNull List<Change.Line> lines, @NonNull Range range) {

    /**
     * Copies lines and checks the range count and line termination.
     *
     * @throws IllegalArgumentException when count or termination is invalid
     */
    public Fragment {
      lines = List.copyOf(lines);
      validate(lines, range);
    }

    /**
     * Checks local fragment shape without inspecting a complete file.
     *
     * @param lines exact lines in file order
     * @param range described range
     * @throws IllegalArgumentException when counts differ or a non-terminal line is
     *                                  unterminated
     */
    public static void validate(@NonNull List<Change.Line> lines, @NonNull Range range) {
      if (lines.size() != range.count()) {
        throw new IllegalArgumentException("Fragment line count must match its range.");
      }
      if (lines.stream().limit(Math.max(0, lines.size() - 1)).anyMatch(line -> line.ending() == Change.Sequence.NONE)) {
        throw new IllegalArgumentException("Only a terminal line may be unterminated.");
      }
    }

    /**
     * Reconstructs exact characters without normalizing or adding line endings.
     *
     * @return concatenated line text and terminators
     */
    public String text() {
      return lines().stream()
          .map((line) -> line.text() + line.ending().getValue())
          .collect(Collectors.joining());
    }
  }

  /**
   * Finds a deterministic shortest insertion/deletion script and groups edits
   * with up to {@code contextLines} unchanged lines on either side. Touching or
   * overlapping windows merge; adjacent edits merge even with zero context.
   * With three context lines, six unchanged lines between edits merge, while
   * seven leave separate chunks. Equality includes each line's exact ending.
   *
   * Results are immutable, ordered, and contain edits; equal inputs return an
   * empty list. Trace-based Myers search uses O((N + M)(D + 1)) worst-case time
   * and O(N + M + D squared) space, where D is the number of inserted/deleted
   * lines. Extensive rewrites can require substantial trace memory; there is no
   * cutoff or alternative algorithm.
   *
   * @param before       original lines
   * @param after        resulting lines
   * @param contextLines requested unchanged context
   */
  private record Diff(List<Line> before, List<Line> after, int contextLines) {

    /**
     * Groups the complete edit script into contextual ranges.
     *
     * @return immutable generated chunks
     */
    private List<Chunk> chunks() {
      return group(changes());
    }

    /**
     * Trims matching ends for the search and restores them as context.
     *
     * @return complete ordered script
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
          changes(before.subList(0, prefix), Revision.CONTEXT),
          shorten(before.subList(prefix, beforeEnd), after.subList(prefix, afterEnd)).stream(),
          changes(before.subList(beforeEnd, before.size()), Revision.CONTEXT))
          .flatMap(stream -> stream)
          .toList();
    }

    /**
     * Finds a shortest script, retaining reachable diagonals for reconstruction.
     *
     * @param before original lines after trimming common ends
     * @param after  resulting lines after trimming common ends
     * @return shortest list of changes for complete reconstruction
     */
    private static List<Change> shorten(List<Line> before, List<Line> after) {
      if (before.isEmpty()) {
        return changes(after, Revision.INSERTION).toList();
      }
      if (after.isEmpty()) {
        return changes(before, Revision.DELETION).toList();
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
      throw new IllegalStateException("No edit path found.");
    }

    /**
     * Deterministic predecessor choice for search and backtracking.
     *
     * @param previous furthest original positions at the preceding edit depth
     * @param depth    current number of inserted/deleted lines
     * @param index    current reachable diagonal index
     *
     * @return whether insertion is the deterministic predecessor
     */
    private static boolean insertion(int[] previous, int depth, int index) {
      return index == 0 || (index < depth && previous[index - 1] < previous[index]);
    }

    /**
     * Reconstructs the deterministic shortest path from saved frontiers.
     *
     * @param before trimmed original lines
     * @param after  trimmed resulting lines
     * @param trace  frontiers by search depth
     * @return shortest ordered script
     */
    private static List<Change> backtrack(List<Line> before, List<Line> after, List<int[]> trace) {
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
          result.add(new Change(before.get(--x), Revision.CONTEXT));
          y--;
        }
        result.add(inserted
            ? new Change(after.get(--y), Revision.INSERTION)
            : new Change(before.get(--x), Revision.DELETION));
      }
      while (x > 0 && y > 0) {
        result.add(new Change(before.get(--x), Revision.CONTEXT));
        y--;
      }
      Collections.reverse(result);
      return result;
    }

    /**
     * Omitted script entries are unchanged, so they advance both file positions
     * equally.
     *
     * @param script script of changes
     * @return changes grouped into chunks
     */
    private List<Chunk> group(List<Change> script) {
      int[] edits = IntStream.range(0, script.size())
          .filter(index -> script.get(index).revision() != Revision.CONTEXT)
          .toArray();
      List<Chunk> chunks = new ArrayList<>();
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
          Revision revision = script.get(cursor).revision();
          if (revision != Revision.INSERTION) {
            beforePosition++;
          }
          if (revision != Revision.DELETION) {
            afterPosition++;
          }
        }
        chunks.add(new Chunk(script.subList(start, end),
            new Range(beforeStart, beforePosition - beforeStart),
            new Range(afterStart, afterPosition - afterStart)));
      }
      return List.copyOf(chunks);
    }

    /**
     * Clamps a context window without overflowing its end calculation.
     *
     * @param edit script edit index
     * @param size complete script length
     * @return exclusive window end
     */
    private int window(int edit, int size) {
      return (int) Math.min(size, (long) edit + contextLines + 1);
    }

    /**
     * Assigns one edit role to each supplied line.
     *
     * @param lines    exact lines
     * @param revision common edit role
     * @return projected changes
     */
    private static Stream<Change> changes(List<Line> lines, Revision revision) {
      return lines.stream().map(line -> new Change(line, revision));
    }
  }
}
