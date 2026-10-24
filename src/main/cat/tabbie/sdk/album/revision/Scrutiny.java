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
 * An image passes with the state its target reached, and fails with the state
 * it found when its target no longer matches what it describes; {@link Fail}
 * lists what fails each kind of image.
 *
 * @param <T> result type
 */
public sealed interface Scrutiny<T> {

  /**
   * Value the scrutiny produced.
   *
   * @return result, or null when the scrutiny carries none, as a failure of a
   *         composite part does
   */
  T result();

  /**
   * Describes work that passed: every check succeeded, and its effects were
   * carried out on the plate.
   *
   * @param <T>    result type
   * @param result produced value
   */
  record Pass<T>(@NonNull T result) implements Scrutiny<T> {

  }

  /**
   * Describes work that did not pass. The check ran and found that the work
   * cannot be carried out as described; that is an outcome, not an error.
   *
   * <p>
   * Each kind of image fails when its target no longer matches what it
   * describes:
   * <ul>
   * <li>An {@link Image.Installation} fails with the file found when the file
   * is not in its before state, because it was changed, deleted or created
   * since rehearsal, and without a state when its path is blocked, because it
   * is a directory, lies beneath a file, or cannot be written.</li>
   * <li>An {@link Image.Configuration} fails with the file's current text when
   * its lines no longer match its chunks' before fragments, and without a state
   * when the file is gone.</li>
   * <li>A {@link Image.Provision} fails with the claim found when the claim is
   * not in its before state, and without a state when no package manager on
   * the machine can make the change.</li>
   * </ul>
   *
   * @param <T>    result type
   * @param result the state found, or null when the failure carries none
   *
   * @apiNote composites report a failed part with {@link #Fail()}, since a
   *          failure carries no result forward.
   */
  record Fail<T>(T result, Exception failure) implements Scrutiny<T> {

    /**
     * Describes a failure that produced a result non-exceptionally
     * 
     * @param result the state found
     */
    public Fail(@NonNull T result) {
      this(result, null);
    }

    /**
     * Describes a failure without a result.
     */
    public Fail() {
      this(null, null);
    }
  }
}