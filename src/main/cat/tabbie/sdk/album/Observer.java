package cat.tabbie.sdk.album;

/**
 * Releases this store's resources according to its documented lifetime.
 * Artifact image generation never closes caller-owned stores.
 *
 * @throws Exception when an observation has failed
 */
interface Observer<Observe, Transfer> {

	/**
	 * Observer that discards every notification.
	 */
	Observer<?, ?> NONE = new Observer<>() {
	};

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