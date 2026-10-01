package cat.tabbie.sdk.album.repository;

import java.io.IOException;

/**
 * A complete repository combining both content roles: it describes its own
 * primary content ({@link Describe}) and retains other described content
 * ({@link Extract}), reopening retained bytes with
 * {@link Extract#retained(Reference.Captured)}. Contracts that need only one
 * role hold a {@link Describe} or an {@link Extract} rather than a store.
 * Implementations may acquire remote, local, cached, or generated content and
 * document their concurrency, limits, and retention lifetime.
 */
public interface Store extends AutoCloseable, Describe, Extract {

	/**
	 * Checks whether primary content is already locally available, without
	 * acquiring it. Sources without local retention return false by default.
	 * This is an observation, not a reservation or an installation-state check.
	 *
	 * @return whether primary content is locally available
	 * @throws IOException when availability cannot be checked
	 */
	default boolean exists() throws IOException {
		return false;
	}

	/**
	 * Releases store resources according to its documented lifetime. Installers
	 * never close caller-owned stores. Stateless stores need no cleanup.
	 *
	 * @throws IOException when resources cannot be released
	 */
	@Override
	default void close() throws IOException {
	}
}