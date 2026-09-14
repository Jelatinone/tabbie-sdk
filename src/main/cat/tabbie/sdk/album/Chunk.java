package cat.tabbie.sdk.album;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cat.tabbie.sdk.album.Chunk.Change.Revision;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

public record Chunk(@NonNull List<Change> changes, @NonNull Range beforeRange, @NonNull Range afterRange) {

  private static final Integer DEFAULT_CONTEXT_LINES = 3;
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
        .forEach((line) -> {
          result.append(line.text()).append(line.ending);
        });
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

  public static List<Chunk> diff(@NonNull String before, @NonNull String after) {
    return diff(before, after, DEFAULT_CONTEXT_LINES);
  }

  public static List<Chunk> diff(@NonNull String before, @NonNull String after, int contextLines) {
    if (contextLines < 0L) {
      throw new IllegalArgumentException("Context line count must be non-negative.");
    }
    if (before.equals(after)) {
      return List.of();
    }

    List<Change.Line> beforeLines = lines(before);
    List<Change.Line> afterLines = lines(after);

    int beforeSize = beforeLines.size();
    int afterSize = afterLines.size();
    int commonSize = Math.min(beforeSize, afterSize);

    int prefix = (int) IntStream.range(0, commonSize)
        .takeWhile(index -> beforeLines.get(index).equals(afterLines.get(index)))
        .count();

    int suffix = (int) IntStream.range(0, commonSize - prefix)
        .takeWhile(index -> beforeLines.get(beforeSize - index - 1)
            .equals(afterLines.get(afterSize - index - 1)))
        .count();

    int start = prefix - Math.min(prefix, contextLines);
    int trailing = Math.min(suffix, contextLines);
    int beforeEnd = beforeSize - suffix;
    int afterEnd = afterSize - suffix;

    List<Change> changes = Stream.of(
        changes(beforeLines, start, prefix, Change.Revision.CONTEXT),
        changes(beforeLines, prefix, beforeEnd, Change.Revision.DELETION),
        changes(afterLines, prefix, afterEnd, Change.Revision.INSERTION),
        changes(beforeLines, beforeEnd, beforeEnd + trailing, Change.Revision.CONTEXT))
        .flatMap(stream -> stream)
        .toList();

    return List.of(new Chunk(changes,
        new Range(start, beforeEnd + trailing - start),
        new Range(start, afterEnd + trailing - start)));
  }

  private static Stream<Change> changes(
      List<Change.Line> lines,
      int start,
      int end,
      Change.Revision kind) {
    return lines.subList((int) start, (int) end).stream()
        .map(line -> new Change(line, kind));
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
