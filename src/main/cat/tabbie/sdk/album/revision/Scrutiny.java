package cat.tabbie.sdk.album.revision;

import cat.tabbie.sdk.album.model.Plate;
import lombok.NonNull;

/**
 * The checked outcome of collapsing an {@link Intermediate} or exposing an
 * {@link Image} on a {@link Plate}. It is the same whether the plate rehearses
 * or acts: work passes when every check succeeded, and fails when a check ran
 * and refused. A check that cannot be made at all throws instead, so a failed
 * check is never disguised as a pass or a failure.
 *
 * <p>
 * An image passes with the state its file reached, and fails with the state
 * it found when its file no longer matches what it describes; {@link Fail}
 * lists what fails each kind of image.
 *
 * @param <T> result type
 */
public sealed interface Scrutiny<T> {

  /**
   * Value the scrutiny produced.
   *
   * @return result, or null when the scrutiny carries none, as a failure of a
   *         composite part or an effect with no result does
   */
  T result();

  /**
   * Describes work that passed: every check succeeded, and its effects were
   * carried out on the plate.
   *
   * @param <T>    result type
   * @param result produced value, or null when the work produces none, as
   *               {@link Intermediate#of(Object) Intermediate.of(null)} does
   */
  record Pass<T>(T result) implements Scrutiny<T> {

  }

  /**
   * Describes work that did not pass. The check ran and found that the work
   * cannot be carried out as described; that is an outcome, not an error.
   *
   * <p>
   * Each kind of image fails when its file no longer matches what it
   * describes:
   * <ul>
   * <li>An {@link Image.Installation} fails with the file found when the file
   * is not in its before state, because it was changed, deleted or created
   * since rehearsal; with the tree's
   * {@link cat.tabbie.sdk.platform.layout.Violation Violation} when its path
   * lies beneath a file or the tree does not permit the change; and with the
   * plate's {@link cat.tabbie.sdk.platform.layout.Refusal Refusal} when its
   * path is blocked, because it is a directory, or cannot be written.</li>
   * <li>An {@link Image.Configuration} fails with the file's current text when
   * its lines no longer match its chunks' before fragments; with the tree's
   * violation when the tree holds no file there or does not permit the
   * change; and with the plate's refusal when the file is gone or cannot be
   * written.</li>
   * </ul>
   *
   * @param <T>     result type
   * @param result  the state found, or null when the failure carries none
   * @param failure what refused the work, such as a plate's refusal, or null
   *                when a check found a state other than the one expected
   *
   * @apiNote composites carry a failed part's {@code failure} forward without
   *          its result, since a failure carries no result forward.
   */
  record Fail<T>(T result, Exception failure) implements Scrutiny<T> {

    /**
     * Describes a failure that found a state other than the one expected.
     *
     * @param result the state found
     */
    public Fail(@NonNull T result) {
      this(result, null);
    }

    /**
     * Describes a failure without a result or a cause.
     */
    public Fail() {
      this(null, null);
    }
  }
}
