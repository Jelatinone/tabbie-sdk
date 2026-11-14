package cat.tabbie.sdk.revision;

import cat.tabbie.sdk.tree.Reason;
import cat.tabbie.sdk.tree.Refusal;
import lombok.NonNull;

/**
 * The checked outcome of collapsing an {@link Intermediate} or exposing an
 * {@link Image} on a {@link Plate}. It is the same whether the plate rehearses
 * or acts: work passes when every check succeeded, and fails with the
 * {@link Reason} a check gave when it ran and refused. A check that cannot be
 * made at all throws instead, so a failed check is never disguised as a pass
 * or a failure.
 *
 * <p>
 * An image passes with the state its place reached, and fails with
 * {@link Reason.Place.Changed} when its place no longer holds what it was
 * described from.
 *
 * @param <Result> what passing work gives
 */
public sealed interface Scrutiny<Result> permits Scrutiny.Pass, Scrutiny.Fail {

  /**
   * Returns what passing work gave, or throws why it failed.
   *
   * @return the result
   * @throws Refusal carrying the reason, when the work failed
   */
  Result orElseThrow() throws Refusal;

  /**
   * Work that passed every check.
   *
   * @param <Result> what it gives
   * @param result   what it gave, possibly null when it gives nothing
   */
  record Pass<Result>(Result result) implements Scrutiny<Result> {

    @Override
    public Result orElseThrow() {
      return result;
    }
  }

  /**
   * Work a check refused, having changed nothing.
   *
   * @param <Result> what it would have given
   * @param reason   why it failed
   */
  record Fail<Result>(@NonNull Reason reason) implements Scrutiny<Result> {

    @Override
    public Result orElseThrow() throws Refusal {
      throw new Refusal(reason);
    }
  }
}
