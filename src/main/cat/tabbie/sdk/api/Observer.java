package cat.tabbie.sdk.api;

/**
 * Callbacks reporting the progress and outcome of one observed operation, such
 * as a content capture. Every callback defaults to doing nothing, so observers
 * override only what they need. Callbacks run on the operation's thread and
 * must not throw.
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
}
