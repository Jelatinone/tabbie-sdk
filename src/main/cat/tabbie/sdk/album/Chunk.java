package cat.tabbie.sdk.album;

import java.util.List;
import java.util.regex.Pattern;

import cat.tabbie.sdk.album.Chunk.Change.Revision;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

public record Chunk(@NonNull List<Change> changes, @NonNull Range beforeRange, @NonNull Range afterRange) {

  private static final Pattern LINE_PATTERN = Pattern.compile("([^\\r\\n]*)(\\r\\n|\\r|\\n|\\z)");

  public Chunk {
    changes = List.copyOf(changes);
  }

  public Fragment before() {
    return fragment(beforeRange, changes, Revision.INSERTION);
  }

  public Fragment after() {
    return fragment(afterRange, changes, Revision.DELETION);
  }

  public String text() {
    StringBuilder result = new StringBuilder();
    changes.stream()
        .map(Change::line)
        .forEach((line) -> result.append(line.text()).append(line.ending));
    return result.toString();
  }

  public Chunk reverse() {
    return new Chunk(
        changes.stream()
            .map(change -> new Change(change.line(), change.revision().reverse()))
            .toList(),
        afterRange,
        beforeRange);
  }

  public static List<Change.Line> lines(@NonNull String text) {
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

  public static void validateFragments(List<Chunk.Fragment> fragments) {
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

  public static void validateChunks(List<Chunk> chunks) {
    int beforeEnd = 0;
    int afterEnd = 0;
    for (Chunk hunk : chunks) {
      int beforeGap = hunk.before().range().count - beforeEnd;
      int afterGap = hunk.after().range().count - afterEnd;
      if (beforeGap < 0 || afterGap < 0 || beforeGap != afterGap) {
        throw new IllegalArgumentException("Hunks must be ordered with matching unchanged gaps.");
      }
    }
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
    }
  }

  public record Range(int start, int count) {

    public Range {
      if (start < 0 || count < 0 || start + count > Integer.MAX_VALUE) {
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
      lines.forEach((change) -> {
        if (change.ending() == Change.Sequence.NONE) {
          throw new IllegalArgumentException("Only a terminal line may be unterminated.");
        }
      });
    }
  }
}
