package cat.tabbie.sdk.api;

import cat.tabbie.sdk.Identity;
import lombok.NonNull;

/**
 * Catalog discovery capability, independent of transport, storage, and content
 * kind. The same contract serves distribution providers (game, loader, and
 * server builds) and addon providers (mods, plugins, resource packs, and
 * similar content). Providers may describe remote, local, cached, or generated
 * content. Implementations return validated immutable declarations and
 * distinguish failed discovery from absent results.
 *
 * <p>
 * Every catalog item a provider publishes carries {@link Coordinate} minted
 * by that provider, so a later process can ask the same provider for the same
 * project, release, or file without shared state.
 *
 * @param <Criterion> provider-specific selection criteria
 * @param <Result>    discovered catalog item, such as a project or release
 */
public interface Provider<Criterion extends Criteria<Identity<Result>>, Result>
    extends Queryable<Criterion, Result> {

  /**
   * Stable provider identity, also carried by every coordinate this provider
   * mints.
   *
   * @return provider identity
   */
  @NonNull
  Identity<Provider<?, ?>> providerId();

  /**
   * Human-readable canonical provider name.
   *
   * @return provider name
   */
  @NonNull
  String providerName();

  /**
   * Mints project coordinates owned by this provider. Implementations should
   * create all coordinates through this method and the navigation methods on
   * its result, so every published item carries this provider's identity.
   *
   * @param projectKey opaque provider project key
   * @return project coordinates owned by this provider
   * @throws IllegalArgumentException when the key is blank
   */
  default Coordinate.Project project(@NonNull String projectKey) {
    return new Coordinate.Project(providerId(), projectKey);
  }

  /**
   * Checks whether coordinates were minted by this provider, for example before
   * resolving coordinates read back from a lockfile.
   *
   * @param coordinates candidate coordinates
   * @return whether this provider owns the coordinates
   */
  default boolean owns(@NonNull Coordinate coordinates) {
    return providerId().equals(coordinates.providerId());
  }

  /**
   * Provider-scoped catalog coordinates. These values carry enough provider
   * information to query the same project, release, or file again. They contain
   * no credentials, provider instances, or transport assumptions. Keys are
   * opaque to everything except the owning provider.
   *
   * <p>
   * Typical mappings:
   * <ul>
   * <li>Mojang: project {@code minecraft}, build {@code 1.21.1}, file
   * {@code client}</li>
   * <li>Paper: project {@code paper}, build {@code 1.21.1-130}, file
   * {@code server}</li>
   * <li>Modrinth: project ID, version ID, file hash or name</li>
   * <li>CurseForge: project ID, file ID, file ID (one file per release)</li>
   * </ul>
   */
  sealed interface Coordinate {

    /**
     * Returns the owning provider identity.
     *
     * @return stable provider identity
     */
    @NonNull
    Identity<Provider<?, ?>> providerId();

    /**
     * Returns this level's opaque provider key.
     *
     * @return nonblank key
     */
    @NonNull
    String key();

    /**
     * A catalog project independent of its releases.
     *
     * @param providerId owning provider identity
     * @param key        exact project key
     */
    record Project(@NonNull Identity<Provider<?, ?>> providerId, @NonNull String key) implements Coordinate {

      /**
       * Validates the opaque nonblank project key.
       */
      public Project {
        validate(key);
      }

      /**
       * Addresses an exact release of this project.
       *
       * @param buildKey exact release key
       * @return release coordinates
       * @throws IllegalArgumentException when the key is blank
       */
      public Build build(@NonNull String buildKey) {
        return new Build(this, buildKey);
      }
    }

    /**
     * An exact provider release.
     *
     * @param project owning project
     * @param key     exact release key
     */
    record Build(@NonNull Project project, @NonNull String key) implements Coordinate {

      /**
       * Validates the release key.
       */
      public Build {
        validate(key);
      }

      @Override
      public Identity<Provider<?, ?>> providerId() {
        return project.providerId();
      }

      /**
       * Addresses an exact payload of this release.
       *
       * @param fileKey provider file key, not necessarily a filesystem name
       * @return file coordinates
       * @throws IllegalArgumentException when the key is blank
       */
      public File file(@NonNull String fileKey) {
        return new File(this, fileKey);
      }
    }

    /**
     * An exact payload within a release.
     *
     * @param build owning release
     * @param key   provider file key, not necessarily a filesystem name
     */
    record File(@NonNull Build build, @NonNull String key) implements Coordinate {

      /**
       * Validates the payload key.
       */
      public File {
        validate(key);
      }

      @Override
      public Identity<Provider<?, ?>> providerId() {
        return build.providerId();
      }

      /**
       * Returns the project owning this payload's release.
       *
       * @return owning project
       */
      public Project project() {
        return build.project();
      }
    }

    /**
     * Checks a coordinate key for construction.
     *
     * @param key candidate key
     * @throws IllegalArgumentException when the key is blank
     */
    private static void validate(String key) {
      if (key.isBlank()) {
        throw new IllegalArgumentException("Provider coordinates must be nonblank.");
      }
    }
  }
}