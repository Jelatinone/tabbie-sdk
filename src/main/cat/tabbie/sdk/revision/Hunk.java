package cat.tabbie.sdk.revision;

import java.util.List;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * One contiguous region of a text change, with its unchanged context. Each
 * change, in order, consumes one line before unless it is an insertion, and
 * one line after unless it is a deletion; context consumes a line on both
 * sides. Starting at the two ranges therefore fixes every change's position,
 * and the lines each side consumes number its range's count.
 *
 * <p>
 * Each side projects to exactly one {@link Fragment}, which is empty at an
 * insertion or deletion boundary. A hunk written by hand may be empty or hold
 * only context. Across a file, a {@link Revision.Diff diff} keeps its hunks in
 * order and apart on both sides, with unchanged gaps of equal length between
 * them, and it is the diff that finds, matches, applies and composes hunks.
 *
 * <p>
 * Ranges start at zero and end exclusively. Lines are equal only with the same
 * ending (LF, CRLF, CR, or none), and a line's text holds no newline character.
 * An unterminated line can only end its side. A hunk applies only at the lines
 * its ranges name, so describing one reads the text it changes.
 *
 * @param changes     context and edits, in order
 * @param beforeRange the lines it spans before
 * @param afterRange  the lines it spans after
 */
public record Hunk(@NonNull List<Change> changes, @NonNull Range beforeRange, @NonNull Range afterRange) {

  /**
   * Copies the changes, and checks the lines each side consumes against its
   * range.
   *
   * @throws IllegalArgumentException when a side consumes more or fewer lines
   *                                  than its range counts, or a line other
   *                                  than its last is unterminated
   */
  public Hunk {
    changes = List.copyOf(changes);
    Fragment.validate(side(changes, Change.Role.INSERTION), beforeRange);
    Fragment.validate(side(changes, Change.Role.DELETION), afterRange);
  }

  /**
   * Returns the lines before, every change's but the inserted ones.
   *
   * @return the side before, at its range
   */
  public Fragment before() {
    return new Fragment(side(changes, Change.Role.INSERTION), beforeRange);
  }

  /**
   * Returns the lines after, every change's but the deleted ones.
   *
   * @return the side after, at its range
   */
  public Fragment after() {
    return new Fragment(side(changes, Change.Role.DELETION), afterRange);
  }

  /**
   * Returns the hunk that undoes this one: its ranges exchanged and each
   * change's role inverted, keeping the changes' order and exact lines.
   *
   * @return the inverse hunk
   */
  public Hunk invert() {
    return new Hunk(
        changes.stream()
            .map(change -> new Change(change.line(), change.role().invert()))
            .toList(),
        afterRange,
        beforeRange);
  }

  /**
   * Whether this hunk edits its text: it inserts or deletes at least one line,
   * rather than being empty or holding only context.
   *
   * @return true when the hunk changes something
   */
  public boolean edits() {
    return changes.stream().anyMatch(change -> change.role() != Change.Role.CONTEXT);
  }

  /**
   * Lists the lines one side consumes: every change's line but those of the
   * role the side skips.
   */
  private static List<Change.Line> side(List<Change> changes, Change.Role skipped) {
    return changes.stream()
        .filter(change -> change.role() != skipped)
        .map(Change::line)
        .toList();
  }

  /**
   * One exact line and its role. Its position follows from its hunk's ranges
   * and the changes before it, rather than being held on each change.
   *
   * @param line the exact text and ending
   * @param role what the line does in the change
   */
  public record Change(@NonNull Line line, @NonNull Role role) {

    /**
     * The exact characters ending a line, or none.
     */
    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public enum Sequence {

      /**
       * A line feed.
       */
      LF("\n"),

      /**
       * A carriage return followed by a line feed.
       */
      CRLF("\r\n"),

      /**
       * A carriage return.
       */
      CR("\r"),

      /**
       * No ending, which only the last line of a side may have.
       */
      NONE("");

      /**
       * The ending's exact characters.
       *
       * @return the characters, empty for no ending
       */
      String value;
    }

    /**
     * What a line does in a change, and so which sides consume it.
     */
    public enum Role {

      /**
       * Inserted: consumes no line before and one line after.
       */
      INSERTION {
        @Override
        public Role invert() {
          return DELETION;
        }
      },

      /**
       * Unchanged context: consumes one line on both sides.
       */
      CONTEXT {
        @Override
        public Role invert() {
          return CONTEXT;
        }
      },

      /**
       * Deleted: consumes one line before and no line after.
       */
      DELETION {
        @Override
        public Role invert() {
          return INSERTION;
        }
      };

      /**
       * Returns the role a line takes when its change is undone: insertion
       * and deletion exchange, and context stays.
       *
       * @return the inverse role
       */
      public abstract Role invert();
    }

    /**
     * A line's text without its ending, and its exact ending.
     *
     * @param text   the line's characters, without CR or LF
     * @param ending its exact ending, or none
     */
    public record Line(@NonNull String text, @NonNull Sequence ending) {

      /**
       * Checks the text holds no ending.
       *
       * @throws IllegalArgumentException when the text holds CR or LF
       */
      public Line {
        if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
          throw new IllegalArgumentException("A line's text holds no newline characters.");
        }
      }

      /**
       * Returns the line's exact characters, its text and then its ending.
       *
       * @return the exact characters
       */
      public String exact() {
        return text.concat(ending.getValue());
      }
    }
  }

  /**
   * A zero-based, half-open range of lines. A count of zero is a boundary,
   * where lines are inserted or were deleted.
   *
   * @param start the first line, or the boundary
   * @param count how many lines
   */
  public record Range(int start, int count) {

    /**
     * Checks the range has a non-negative start and count, and an end that
     * fits.
     *
     * @throws IllegalArgumentException when the start or count is negative, or
     *                                  the end overflows
     */
    public Range {
      if (start < 0 || count < 0 || (long) start + count > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("A line range has a non-negative start and count, and an end that fits.");
      }
    }

    /**
     * Returns the range's exclusive end.
     *
     * @return the start plus the count
     */
    public int end() {
      return start + count;
    }
  }

  /**
   * One side of a hunk: its lines, contiguous, at their range, which may be an
   * empty boundary.
   *
   * @param lines the exact lines, in order
   * @param range the lines' range
   */
  public record Fragment(@NonNull List<Change.Line> lines, @NonNull Range range) {

    /**
     * Copies the lines, and checks them against the range.
     *
     * @throws IllegalArgumentException when the lines number other than the
     *                                  range's count, or a line other than the
     *                                  last is unterminated
     */
    public Fragment {
      lines = List.copyOf(lines);
      validate(lines, range);
    }

    /**
     * Returns the fragment's exact characters, every line's text and ending,
     * neither normalized nor added to.
     *
     * @return the exact characters
     */
    public String text() {
      return lines.stream()
          .map(Change.Line::exact)
          .collect(Collectors.joining());
    }

    /**
     * Checks lines against the range they are at, for this constructor and
     * for a hunk checking each side.
     */
    private static void validate(List<Change.Line> lines, Range range) {
      if (lines.size() != range.count()) {
        throw new IllegalArgumentException("A fragment's lines number its range's count.");
      }
      if (lines.stream()
          .limit(Math.max(0, lines.size() - 1))
          .anyMatch(line -> line.ending() == Change.Sequence.NONE)) {
        throw new IllegalArgumentException("Only a fragment's last line may be unterminated.");
      }
    }
  }
}
