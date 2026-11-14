package cat.tabbie.sdk.platform;

import java.util.Locale;
import java.util.OptionalLong;

import cat.tabbie.sdk.tree.Reason;
import lombok.NonNull;

/**
 * The one sink for what happens while work is carried out: the progress of a
 * transfer or an extraction, and failures. Steps report through the observer
 * their plate gives them, and the shapes core builds for that plate report
 * their reads to it. The command line passes one that prints each event.
 */
@FunctionalInterface
public interface Observer {

  /**
   * An observer that ignores every event.
   */
  Observer NONE = event -> {
  };

  /**
   * Receives one event.
   *
   * @param event what happened
   */
  void observe(@NonNull Event event);

  /**
   * Something that happened, which renders itself for a person.
   */
  sealed interface Event permits Progress, Failure {

    /**
     * Renders the event as one line for a person.
     *
     * @return the rendered line
     */
    @NonNull
    String render();
  }

  /**
   * How far work on a subject has come, counted in a unit.
   *
   * @param subject   what the work is on, such as a file's address
   * @param completed how much is done
   * @param total     how much there is, or empty when it is not known
   * @param unit      what is counted
   */
  record Progress(
      @NonNull String subject,
      long completed,
      @NonNull OptionalLong total,
      @NonNull Unit unit) implements Event {

    /**
     * Checks the counts.
     *
     * @throws IllegalArgumentException when a count is negative
     */
    public Progress {
      if (completed < 0 || total.isPresent() && total.getAsLong() < 0) {
        throw new IllegalArgumentException("Progress counts are not negative.");
      }
    }

    @Override
    public String render() {
      String done = unit.render(completed);
      if (total.isEmpty() || total.getAsLong() == completed) {
        return String.format("%s  %s", subject, done);
      }
      long whole = total.getAsLong();
      return String.format(Locale.ROOT, "%s  %s of %s (%d%%)", subject, done, unit.render(whole),
          whole == 0 ? 100 : completed * 100 / whole);
    }
  }

  /**
   * Work on a subject that failed.
   *
   * @param subject what the work was on
   * @param reason  why it failed
   */
  record Failure(@NonNull String subject, @NonNull Reason reason) implements Event {

    @Override
    public String render() {
      return String.format("%s  %s", subject, reason.message());
    }
  }

  /**
   * What progress counts.
   */
  enum Unit {

    /**
     * Bytes, rendered in binary multiples.
     */
    BYTE {
      @Override
      public String render(long count) {
        if (count < 1024) {
          return String.format(Locale.ROOT, "%d B", count);
        }
        int exponent = (63 - Long.numberOfLeadingZeros(count)) / 10;
        return String.format(Locale.ROOT, "%.1f %ciB", count / Math.pow(1024, exponent), "KMGTPE".charAt(exponent - 1));
      }
    },

    /**
     * Archive entries.
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
     * Renders a count of this unit.
     *
     * @param count how many
     * @return the rendered count
     */
    public abstract String render(long count);
  }
}
