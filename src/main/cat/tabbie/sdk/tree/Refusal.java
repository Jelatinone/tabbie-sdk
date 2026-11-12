package cat.tabbie.sdk.tree;

import java.io.IOException;
import java.io.Serial;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Thrown when work is refused while it is read or carried out, and changed
 * nothing: a plate looked, and the path or its lease did not allow the
 * operation. {@link #reason()} says why. Failures while reading or writing are
 * other {@link IOException IOExceptions}. It is the counterpart of a
 * {@link Violation}, which is found while work is still being described.
 *
 * <p>
 * Callers that decide whether work passes or fails catch the refusal and
 * switch over its reason, or ask whether it is
 * {@link Reason#retryable() retryable}; callers that do not care let it
 * propagate.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Refusal extends IOException {

  @Serial
  private static final long serialVersionUID = 1L;

  transient Reason reason;

  /**
   * Refuses work.
   *
   * @param reason why the work was refused
   */
  public Refusal(@NonNull Reason reason) {
    this(reason, null);
  }

  /**
   * Refuses work, for a reason a failure revealed.
   *
   * @param reason why the work was refused
   * @param cause  failure that revealed it, or null
   */
  public Refusal(@NonNull Reason reason, Throwable cause) {
    super(reason.message(), cause);
    this.reason = reason;
  }

  /**
   * Returns why the work was refused.
   *
   * @return refusal reason
   */
  public Reason reason() {
    return reason;
  }
}
