package cat.tabbie.sdk.album.repository;

import java.io.IOException;

/**
 * A source with a lifetime: it describes its own primary content
 * ({@link Describe}) and may hold resources, such as a cache or a connection,
 * until it is closed. Retaining content is not its role; work captures through
 * the {@link Stash} of the plate it runs on. Implementations may acquire
 * remote, local, cached, or generated content and document their concurrency,
 * limits, and lifetime.
 */
public interface Store extends AutoCloseable, Describe {

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
