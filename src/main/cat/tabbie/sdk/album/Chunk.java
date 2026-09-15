package cat.tabbie.sdk.album;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cat.tabbie.sdk.album.Chunk.Change.Line;
import cat.tabbie.sdk.album.Chunk.Change.Revision;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 *
 * <h1>Chunk</h1>
 *
 * <p>
 * One contiguous region of a text change, including its unchanged context. Each
 * ordered change consumes one before line unless it is an insertion, and one
 * after line unless it is a deletion. Context consumes a line on both sides.
 * Starting at the two ranges therefore determines every change's positions;
 * the consumed counts must equal the respective range counts.
 * </p>
 *
 * <p>
 * Each side projects to exactly one fragment, including zero-length fragments
 * at insertion/deletion boundaries. Manually authored chunks may be empty or
 * context-only. Across a file, chunks must be ordered and non-overlapping on
 * both sides, with equal lengths of omitted unchanged gaps.
 * </p>
 */
public record Chunk(@NonNull List<Change> changes, @NonNull Range beforeRange, @NonNull Range afterRange) {

  private static final Pattern LINE_PATTERN = Pattern.compile("([^\\r\\n]*)(\\r\\n|\\r|\\n|\\z)");
  private static final Integer CONTEXT_LINES = 3;

  public Chunk {
    changes = List.copyOf(changes);
    validateFragment(beforeRange, changes, Revision.INSERTION);
    validateFragment(afterRange, changes, Revision.DELETION);
  }

  /**
   * Contigous original text, excluding any inserted lines
   *
   * @return original text
   */
  public Fragment before() {
    return fragment(beforeRange, changes, Revision.INSERTION);
  }

  /**
   * Contigous resulting text, excluding deleted lines
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
  public Chunk reverse() {
    return new Chunk(
        changes.stream()
            .map(change -> new Change(change.line(), change.revision().reverse()))
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

  public static List<Chunk> diff(@NonNull String before, @NonNull String after) {
    return diff(before, after, CONTEXT_LINES);
  }

  public static List<Chunk> diff(@NonNull String before, @NonNull String after, int contextLines) {
    if (contextLines < 0) {
      throw new IllegalArgumentException("Context line count must be non-negative.");
    }
    return before.equals(after) ? List.of() : new Diff(linesOf(before), linesOf(after), contextLines).chunks();
  }

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
        unterminated = fragment.lines().get(fragment.lines().size() - 1).ending() == Change.Sequence.NONE;
      }
    }
  }

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
      if (beforeGap < 0 || afterGap < 0 || beforeGap != afterGap) {
        throw new IllegalArgumentException("Fragments must be ordered with matching unchanged gaps.");
      }
      beforeEnd = beforeRange.end();
      afterEnd = afterRange.end();
    }
  }

  public static void validateChunks(@NonNull List<Chunk> chunks) {
    validateFragments(
        chunks.stream()
            .map(Chunk::before)
            .toList(),
        chunks.stream()
            .map(Chunk::after)
            .toList());
  }

  private static void validateFragment(@NonNull Range range, @NonNull List<Change> changes,
      @NonNull Change.Revision excluded) {
    Fragment.validate(
        changes.stream()
            .filter(entry -> entry.revision() != excluded)
            .map(Change::line)
            .toList(),
        range);
  }

  private static Fragment fragment(@NonNull Range range, @NonNull List<Change> changes,
      @NonNull Change.Revision excluded) {
    return new Fragment(
        changes.stream()
            .filter(entry -> entry.revision() != excluded)
            .map(Change::line)
            .toList(),
        range);
  }

  public record Change(@NonNull Line line, @NonNull Revision revision) {

    @AllArgsConstructor
    public enum Sequence {

      LF("\n"),

      CRLF("\r\n"),

      CR("\r"),

      NONE("");

      @Getter
      private final String value;
    }

    public enum Revision {

      INSERTION,

      CONTEXT,

      DELETION;

      public Revision reverse() {
        return switch (this) {
          case CONTEXT -> CONTEXT;
          case DELETION -> INSERTION;
          case INSERTION -> DELETION;
        };
      }
    }

    public record Line(@NonNull String text, @NonNull Sequence ending) {
      public Line {
        if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
          throw new IllegalArgumentException("Line text must not contain newline characters.");
        }
      }
    }
  }

  public record Range(int start, int count) {

    public Range {
      if (start < 0 || count < 0 || (long) start + count > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Line range must have a valid non-negative end.");
      }
    }

    public int end() {
      return start + count;
    }
  }

  public record Fragment(@NonNull List<Change.Line> lines, @NonNull Range range) {

    public Fragment {
      lines = List.copyOf(lines);
      validate(lines, range);
    }

    public static void validate(@NonNull List<Change.Line> lines, @NonNull Range range) {
      if (lines.size() != range.count()) {
        throw new IllegalArgumentException("Fragment line count must match its range.");
      }
      if (lines.stream().limit(Math.max(0, lines.size() - 1)).anyMatch(line -> line.ending() == Change.Sequence.NONE)) {
        throw new IllegalArgumentException("Only a terminal line may be unterminated.");
      }
    }

    public String text() {
      return lines().stream()
          .map((line) -> line.text() + line.ending().getValue())
          .collect(Collectors.joining());
    }
  }

  /**
   *
   * <h1>Diff</h1>
   *
   * <p>
   * Finds a deterministic shortest insertion/deletion script and groups edits
   * with up to {@code contextLines} unchanged lines on either side. Touching or
   * overlapping windows merge; adjacent edits merge even with zero context.
   * With three context lines, six unchanged lines between edits merge, while
   * seven leave separate chunks. Equality includes each line's exact ending.
   * </p>
   *
   * <p>
   * Results are immutable, ordered, and contain edits; equal inputs return an
   * empty list. Trace-based Myers search uses O((N + M)(D + 1)) worst-case time
   * and O(N + M + D squared) space, where D is the number of inserted/deleted
   * lines. Extensive rewrites can require substantial trace memory; there is no
   * cutoff or alternative algorithm.
   * </p>
   *
   * @throws IllegalArgumentException when context lines are negative
   */
  private record Diff(List<Line> before, List<Line> after, int contextLines) {

    private List<Chunk> chunks() {
      return group(changes());
    }

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
     * Retain only reachable diagonals for reconstruction for each search depth.
     *
     * @param before diagonal before
     * @param after  diagonal after
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
     * @param previous previous
     * @param depth    depth
     * @param index    index
     *
     * @return insertion
     */
    private static boolean insertion(int[] previous, int depth, int index) {
      return index == 0 || (index < depth && previous[index - 1] < previous[index]);
    }

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

    private int window(int edit, int size) {
      return (int) Math.min(size, (long) edit + contextLines + 1);
    }

    private static Stream<Change> changes(List<Line> lines, Revision revision) {
      return lines.stream().map(line -> new Change(line, revision));
    }
  }
}
