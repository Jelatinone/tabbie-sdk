package cat.tabbie.sdk.album.revision;

/**
 * The outcome of checking whether an {@link Intermediate} or {@link Image} can
 * be carried out as described, without carrying it out. Rehearsing
 * scrutinizes; acting carries the work out. A scrutiny passes, fails or mocks.
 * A check that cannot be made at all throws instead, so a failed check is
 * never disguised as a pass, fail or mock.
 *
 * <p>
 * An intermediate scrutinizes the way it collapses, keeping its effects within
 * scratch space and the retention backend. Steps whose effects stay there pass
 * with the result collapsing produces; other steps mock, unless they override
 * scrutiny to check their preconditions.
 *
 * <p>
 * An image scrutinizes against its target as it is now. It passes when the
 * target is in the image's before state and acting can bring it to the after
 * state, and fails when the target no longer matches what the image
 * describes; {@link Fail} lists what fails each kind of image.
 *
 * @param <T> result type
 */
public sealed interface Scrutiny<T> {

  /**
   * Value the scrutiny produced.
   *
   * @return scrutinized value, or null when the scrutiny carries none, as a
   *         mock or a failure of a composite part does
   */
  T result();

  /**
   * Describes a scrutiny that passed: every check succeeded, so carrying the
   * work out now would succeed as described, unless its targets change first.
   *
   * @param <T>    result type
   * @param result scrutinized value
   */
  record Pass<T>(T result) implements Scrutiny<T> {

  }

  /**
   * Describes a scrutiny that did not pass. The check ran and found that the
   * work cannot be carried out as described; that is an outcome, not an error.
   *
   * <p>
   * Each kind of image fails when its target no longer matches what it
   * describes:
   * <ul>
   * <li>An {@link Image.Installation} fails when the file is not in its before
   * state, because it was changed, deleted or created since rehearsal, or
   * when its path is blocked, because it is a directory or is not writable.
   * Whether its after content is still retained is the consumer's check.</li>
   * <li>A {@link Image.Configuration} fails when the file's lines no longer
   * match its chunks' before fragments, or the file is gone.</li>
   * <li>A {@link Image.Provision} fails when the claim is not in its before
   * state, or when no package manager on the host can make the change: taking
   * a claim needs a manager that carries the package, and releasing one needs
   * the manager that installed it.</li>
   * </ul>
   *
   * @param <T>    result type
   * @param result scrutinized value, or null when the failure carries none
   *
   * @apiNote composites report a failed part with {@link #Fail()}, since a
   *          failure carries no result forward.
   */
  record Fail<T>(T result) implements Scrutiny<T> {

    /**
     * Describes a failure without a result.
     */
    public Fail() {
      this(null);
    }
  }

  /**
   * Describes a scrutiny that could not check the work without effects
   * reaching beyond scratch space, and that is therefore assumed to pass. A
   * bare effect, or a step that does not override scrutiny, mocks.
   *
   * @param <T>    result type
   * @param result empty result
   *
   * @apiNote constructor {@link #Mock()} is preferred to {@link #Mock(Object)}.
   */
  record Mock<T>(T result) implements Scrutiny<T> {

    /**
     * Describes a mock without a result.
     */
    public Mock() {
      this(null);
    }
  }
}
