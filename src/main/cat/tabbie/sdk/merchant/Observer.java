package cat.tabbie.sdk.merchant;

/**
 * Releases this store's resources according to its documented lifetime.
 * Artifact image generation never closes caller-owned stores.
 */
public interface Observer<Observe, Transfer> {

  /**
   * Observer that discards every notification.
   */
  @SuppressWarnings("rawtypes")
  Observer NONE = new Observer<>() {
  };

  @SuppressWarnings("unchecked")
  static <Observe, Transfer> Observer<Observe, Transfer> none() {
    return (Observer<Observe, Transfer>) NONE;
  }

  /**
   * Observe in-progress transfer statistics
   *
   * @param transfer transfer state
   */
  default void transfer(Transfer transfer) {
  }

  /**
   * Observe failed transfer verification of a capture
   *
   * @param failure failed exception
   */
  default void failed(Exception failure) {
  }

  /**
   * Observe complete transfer verification of a capture
   *
   * @param observe verified observation
   */
  default void verified(Observe observe) {
  }
}
