package cat.tabbie.sdk.merchant;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.api.Criteria;
import cat.tabbie.sdk.api.Queryable;
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
     * @return non-blank key
     */
    @NonNull
    String key();

    /**
     * Returns the project this coordinate belongs to, or itself for a project.
     *
     * @return owning project coordinates
     */
    @NonNull
    Project project();

    /**
     * Checks whether the other coordinate is this coordinate or addressed beneath
     * it. A project includes its releases and their files, a release includes
     * its files, and a file includes only itself. Dependency and conflict
     * declarations match candidate content by this rule.
     *
     * @param other candidate coordinate
     * @return whether this coordinate includes the other
     */
    default boolean includes(@NonNull Coordinate other) {
      return switch (this) {
        case Project project -> project.equals(other.project());
        case Build build -> build.equals(other) || other instanceof File file && build.equals(file.build());
        case File file -> file.equals(other);
      };
    }

    /**
     * Returns stable, unambiguous text of the form
     * {@code provider/project[/build[/file]]}, with the provider UUID followed
     * by each key URL-encoded as UTF-8. Equal coordinates, and only equal
     * coordinates, share canonical text, so it is suitable for persistence and
     * for deriving identities with {@link Identity#create(String)}.
     *
     * @return canonical coordinate text
     */
    default String canonical() {
      return switch (this) {
        case Project project -> project.providerId().id() + "/" + encode(project.key());
        case Build build -> build.project().canonical() + "/" + encode(build.key());
        case File file -> file.build().canonical() + "/" + encode(file.key());
      };
    }

    /**
     * A catalog project independent of its releases.
     *
     * @param providerId owning provider identity
     * @param key        exact project key
     */
    record Project(@NonNull Identity<Provider<?, ?>> providerId, @NonNull String key) implements Coordinate {

      /**
       * Validates the opaque non-blank project key.
       */
      public Project {
        validate(key);
      }

      @Override
      public Project project() {
        return this;
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
        throw new IllegalArgumentException("Provider coordinates must be non-blank.");
      }
    }

    /**
     * Escapes an opaque key so separators in canonical text stay unambiguous.
     *
     * @param key opaque key
     * @return URL-encoded key
     */
    private static String encode(String key) {
      return URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
  }
}
