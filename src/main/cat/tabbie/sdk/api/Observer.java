package cat.tabbie.sdk.api;

import java.util.Locale;

import lombok.NonNull;

/**
 * Callbacks reporting the progress and outcome of one observed operation, such
 * as a content capture. Every callback defaults to doing nothing, so observers
 * override only what they need. Callbacks run on the operation's thread and
 * must not throw.
 *
 * <p>
 * An operation that takes an observer directly reports its own shapes to it.
 * Intermediate steps report to their context's observer instead, which accepts
 * any {@link Renderable} report, since every step in the work shares it.
 *
 * @param <Observe>  verified outcome type
 * @param <Transfer> progress report type
 */
public interface Observer<Observe, Transfer> {

  /**
   * Observer that discards every notification.
   */
  @SuppressWarnings("rawtypes")
  Observer NONE = new Observer<>() {
  };

  /**
   * Returns the observer that discards every notification, typed for the
   * caller.
   *
   * @param <Observe>  verified outcome type
   * @param <Transfer> progress report type
   * @return shared discarding observer
   */
  @SuppressWarnings("unchecked")
  static <Observe, Transfer> Observer<Observe, Transfer> none() {
    return (Observer<Observe, Transfer>) NONE;
  }

  /**
   * Observes in-progress transfer statistics.
   *
   * @param transfer transfer state
   */
  default void transfer(Transfer transfer) {
  }

  /**
   * Observes a failed operation, such as a capture whose verification failed.
   * The failure is still thrown to the caller.
   *
   * @param failure failure ending the operation
   */
  default void failed(Exception failure) {
  }

  /**
   * Observes a completed and verified operation.
   *
   * @param observe verified outcome
   */
  default void verified(Observe observe) {
  }

  /**
   * A report any observer can show without knowing its shape. An observer
   * that knows a reporter's own record type may match on it for the full
   * shape. Reports carry no timing; whoever runs the work times its steps.
   */
  interface Renderable {

    /**
     * Renders this report as one line.
     *
     * @return human-readable report
     */
    @NonNull
    String render();
  }

  /**
   * Unit a report counts its progress in, such as the bytes of a transfer or
   * the entries of an archive.
   */
  enum Operand {

    /**
     * Bytes, in the largest binary unit that keeps the count at least one,
     * such as {@code 3.2 MiB}.
     */
    BYTE {
      @Override
      public String render(long count) {
        if (count < 1024) {
          return count + " B";
        }
        int exponent = (63 - Long.numberOfLeadingZeros(count)) / 10;
        return String.format(Locale.ROOT, "%.1f %ciB", count / Math.pow(1024, exponent), "KMGTPE".charAt(exponent - 1));
      }
    },

    /**
     * Central-directory entries, files and directories alike.
     */
    ENTRY {
      @Override
      public String render(long count) {
        return String.format(Locale.ROOT, count == 1 ? "%d entry" : "%d entries", count);
      }
    },

    /**
     * Files.
     */
    FILE {
      @Override
      public String render(long count) {
        return String.format(Locale.ROOT, count == 1 ? "%d file" : "%d files", count);
      }
    };

    /**
     * Renders a count in this unit.
     *
     * @param count count to render
     * @return human-readable count
     */
    public abstract String render(long count);
  }
}
