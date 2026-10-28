package cat.tabbie.sdk.album.repository;

import java.io.IOException;

import cat.tabbie.sdk.album.model.Plate;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.album.revision.Scrutiny;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Reference;
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
 * it runs on, which is a stash; a {@link Capture} describes a capture to run
 * when collapsed.
 */
public interface Stash {

  /**
   * Captures a source: reads it, verifies any evidence it declares, and
   * retains its bytes under their own identity. Implementations may use
   * {@link Describe#transfer(java.io.OutputStream, Observer)} to digest while
   * writing.
   *
   * @param source   description of the bytes to capture
   * @param observer transfer callbacks; callbacks must not throw
   * @return verified reference to the retained bytes
   * @throws IOException when the source cannot be read, its evidence differs,
   *                     or the bytes cannot be retained
   */
  @NonNull
  Reference.Captured capture(@NonNull Describe source,
      @NonNull Observer<? super Reference.Captured, ? super Reference.Transfer> observer) throws IOException;

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
   * retained here or were reclaimed. Its {@link Describe#path() path} is one
   * the bytes were captured under; when identical bytes were captured under
   * several, it may be any of them.
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
   * A capture described to run later: collapsing it captures the source into
   * the plate's stash and passes with the captured reference. Capturing changes
   * no file beneath the plate's root, so it runs alike while rehearsing and
   * acting.
   *
   * <p>
   * It reports to the plate's observer as it runs: each
   * {@link Reference.Transfer} as the stash reads the source, then the
   * completed transfer, or the failure that ended it.
   *
   * @param source description of the bytes to capture
   */
  record Capture(@NonNull Describe source) implements Intermediate.Step<Reference.Captured> {

    @Override
    public String name() {
      Reference.Pending pending = source.of();
      return pending.expectedSize() == null
          ? String.format("Capture %s", source.fileName())
          : String.format("Capture %s (%s)", source.fileName(), Observer.Operand.BYTE.render(pending.expectedSize()));
    }

    @Override
    public Scrutiny<Reference.Captured> collapse(@NonNull Plate plate) throws IOException {
      return new Scrutiny.Pass<>(plate.capture(source));
    }
  }
}
