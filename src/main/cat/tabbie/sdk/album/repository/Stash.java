package cat.tabbie.sdk.album.repository;

import java.io.IOException;
import java.util.Locale;
import java.util.OptionalLong;

import cat.tabbie.sdk.album.model.Plate;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.album.revision.Scrutiny;
import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * The store of captured content. A stash captures described sources under
 * their own identity and reopens retained bytes by reference; it is never
 * specific bytes, which a {@link Reference.Captured} names instead. Each
 * successful capture publishes complete bytes verified by digest and size, and
 * a failure publishes no incomplete content. Stashes may deduplicate identical
 * bytes across filenames. Retained bytes stay readable until the stash's
 * documented lifetime ends or their owner explicitly reclaims them; core owns
 * revision and backup retention. Implementations document concurrency and
 * limits.
 *
 * <p>
 * Its methods are immediate effects. Work captures through the {@link Plate}
 * it runs on, which is a stash; {@link Describe#capture()} describes a capture
 * to run when collapsed.
 */
public interface Stash {

  /**
   * Captures a source: reads it, verifies any evidence it declares, and
   * retains its bytes under its own identity. Implementations may use
   * {@link Reference.Pending#transfer} to digest while writing.
   *
   * @param source   description of the bytes to capture
   * @param observer transfer callbacks; callbacks must not throw
   * @return verified reference to the retained bytes
   * @throws IOException when the source cannot be read, its evidence differs,
   *                     or the bytes cannot be retained
   */
  @NonNull
  Reference.Captured capture(@NonNull Describe source,
      @NonNull Observer<? super Reference.Captured, ? super Transfer> observer) throws IOException;

  /**
   * Captures a source without progress callbacks.
   *
   * @param source description of the bytes to capture
   * @return verified reference to the retained bytes
   * @throws IOException when the source cannot be read, its evidence differs,
   *                     or the bytes cannot be retained
   */
  @NonNull
  default Reference.Captured capture(@NonNull Describe source) throws IOException {
    return capture(source, Observer.none());
  }

  /**
   * Describes retained bytes by reference. Describing performs no I/O; opening
   * the result yields exactly the retained bytes, and fails when they are not
   * retained here or were reclaimed.
   *
   * @param reference captured reference previously produced by this stash or
   *                  one sharing its storage
   * @return reusable description of the retained bytes
   */
  @NonNull
  Describe retained(@NonNull Reference.Captured reference);

  /**
   * Reads retained content as UTF-8 text, verified against its reference.
   *
   * @param content retained content
   * @return complete text
   * @throws IOException when the content is not retained here or no longer
   *                     matches its reference
   */
  @NonNull
  default String text(@NonNull Reference.Captured content) throws IOException {
    return retained(content).text();
  }

  /**
   * Checks whether the referenced bytes are retained, without acquiring them.
   * Stashes that cannot tell cheaply may return false, which only causes a
   * redundant capture.
   *
   * @param reference captured reference to check
   * @return whether the bytes are retained
   * @throws IOException when availability cannot be checked
   */
  default boolean exists(@NonNull Reference.Captured reference) throws IOException {
    return false;
  }

  /**
   * Progress of one transfer of bytes, such as a capture, as reported to an
   * observer. A transfer that has read all it expected, or whose total is
   * unknown, renders as the amount read so far.
   *
   * @param subject         what is being transferred, such as a file name or
   *                        an archive entry's path
   * @param transferredSize bytes read so far
   * @param expectedSize    total expected bytes, when known
   */
  record Transfer(
      @NonNull String subject,
      long transferredSize,
      @NonNull OptionalLong expectedSize) implements Observer.Renderable {

    /**
     * Checks non-negative counts.
     *
     * @throws IllegalArgumentException when a count is negative
     */
    public Transfer {
      if (transferredSize < 0) {
        throw new IllegalArgumentException("Transferred bytes must be non-negative.");
      }
      if (expectedSize.isPresent() && expectedSize.getAsLong() < 0) {
        throw new IllegalArgumentException("Expected bytes must be non-negative or empty.");
      }
    }

    @Override
    public String render() {
      String read = Archive.Operand.BYTE.render(transferredSize);
      if (expectedSize.isEmpty() || expectedSize.getAsLong() == transferredSize) {
        return String.format("%s  %s", subject, read);
      }
      long total = expectedSize.getAsLong();
      return String.format(Locale.ROOT, "%s  %s of %s (%d%%)", subject, read, Archive.Operand.BYTE.render(total),
          total == 0 ? 100 : transferredSize * 100 / total);
    }
  }

  /**
   * A capture described to run later: collapsing it captures the source into
   * the plate's stash and passes with the captured reference. Capturing changes
   * no file of the Cat, so it runs alike while rehearsing and acting.
   *
   * <p>
   * It reports to the plate's observer as it runs: each {@link Transfer} as
   * the stash reads the source, then the completed transfer, or the failure
   * that ended it.
   *
   * @param source description of the bytes to capture
   */
  record Capture(@NonNull Describe source) implements Intermediate.Step<Reference.Captured> {

    @Override
    public String name() {
      Reference.Pending pending = source.of();
      return pending.expectedSize() == null
          ? String.format("Capture %s", pending.fileName())
          : String.format("Capture %s (%s)", pending.fileName(), Archive.Operand.BYTE.render(pending.expectedSize()));
    }

    @Override
    public Scrutiny<Reference.Captured> collapse(@NonNull Plate plate) throws IOException {
      Observer<Reference.Captured, Transfer> reports = new Observer<>() {

        @Override
        public void transfer(Transfer transfer) {
          plate.observer().transfer(transfer);
        }

        @Override
        public void verified(Reference.Captured captured) {
          plate.observer().verified(new Transfer(source.of().path(), captured.size(),
              OptionalLong.of(captured.size())));
        }

        @Override
        public void failed(Exception failure) {
          plate.observer().failed(failure);
        }
      };
      return new Scrutiny.Pass<>(plate.capture(source, reports));
    }
  }
}
