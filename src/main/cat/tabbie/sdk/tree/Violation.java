package cat.tabbie.sdk.tree;

import java.io.Serial;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Thrown when work describes something a tree, its rules, an image or the
 * registry does not hold or allow. It is found while work describes its
 * changes, before any of them is carried out, so nothing has changed.
 * {@link #reason()} says why. It is the description-time counterpart of a
 * {@link Refusal}, which refuses work while it is read or carried out.
 *
 * <p>
 * Callers that decide whether work passes or fails catch the violation and
 * switch over its reason; callers that do not care let it propagate.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Violation extends IllegalArgumentException {

  @Serial
  private static final long serialVersionUID = 1L;

  transient Reason reason;

  /**
   * Describes a violation.
   *
   * @param reason why the work is not allowed
   */
  public Violation(@NonNull Reason reason) {
    this(reason, null);
  }

  /**
   * Describes a violation that a failure revealed.
   *
   * @param reason why the work is not allowed
   * @param cause  failure that revealed it, or null
   */
  public Violation(@NonNull Reason reason, Throwable cause) {
    super(reason.message(), cause);
    this.reason = reason;
  }

  /**
   * Returns why the work is not allowed.
   *
   * @return violation reason
   */
  public Reason reason() {
    return reason;
  }
}
