package cat.tabbie.sdk.catalog;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.content.Artifact;
import cat.tabbie.sdk.revision.Intermediate;
import cat.tabbie.sdk.target.Label;
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
 * <p>
 * Core queries every provider with the standard {@link Search} criteria.
 * Querying the provider itself finds projects: by coordinate identifier, by
 * text, or by target and family filters. Querying {@link #releases()} finds
 * releases: a project identifier lists that project's releases newest first,
 * a build identifier selects that release, and a file identifier selects the
 * release containing that file. Providers may accept their own {@link Search}
 * implementations with extra criteria.
 *
 * @param <Result>    discovered project type, such as an addon
 * @param <Published> published release type, such as an addon build
 */
public interface Provider<Result, Published extends Release<?>> extends Queryable<Provider.Search, Result> {

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
   * Queries releases of this provider's projects.
   *
   * @return release queries using the standard criteria
   */
  @NonNull
  Queryable<Search, Published> releases();

  /**
   * Resolves project coordinates, for example from a lockfile.
   *
   * @param coordinates project coordinates minted by this provider
   * @return unevaluated lookup producing the project, or empty when it no
   *         longer exists; collapsing fails with an {@link IOException} when
   *         the provider cannot answer
   */
  default Intermediate<Optional<Result>> resolve(@NonNull Coordinate.Project coordinates) {
    return query(new Query.Singular<>(Search.of(coordinates)));
  }

  /**
   * Resolves an exact release.
   *
   * @param coordinates release coordinates minted by this provider
   * @return unevaluated lookup producing the release, or empty when it no
   *         longer exists; collapsing fails with an {@link IOException} when
   *         the provider cannot answer
   */
  default Intermediate<Optional<Published>> resolve(@NonNull Coordinate.Build coordinates) {
    return releases().query(new Query.Singular<>(Search.of(coordinates)));
  }

  /**
   * Resolves the release containing an exact file.
   *
   * @param coordinates file coordinates minted by this provider
   * @return unevaluated lookup producing the containing release, or empty when
   *         it no longer exists; collapsing fails with an {@link IOException}
   *         when the provider cannot answer
   */
  default Intermediate<Optional<Published>> resolve(@NonNull Coordinate.File coordinates) {
    return releases().query(new Query.Singular<>(Search.of(coordinates)));
  }

  /**
   * Lists a project's newest releases declaring any of the targets, newest
   * first.
   *
   * @param project project coordinates minted by this provider
   * @param targets targets to filter by, empty for every release
   * @param limit   positive maximum release count
   * @return unevaluated listing producing the matching releases, possibly
   *         none; collapsing fails with an {@link IOException} when the
   *         provider cannot list them
   * @throws IllegalArgumentException when the limit is not positive
   */
  default Intermediate<Collection<Published>> builds(@NonNull Coordinate.Project project, @NonNull Set<Label> targets,
      int limit) {
    return releases().query(new Query.Several<>(Search.of(project).withTargets(targets), limit));
  }

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
   * Standard catalog criteria every provider accepts. Absent or empty criteria
   * do not filter.
   */
  interface Search extends Criteria<Coordinate> {

    /**
     * Returns free text to search for.
     *
     * @return search text
     */
    default Optional<String> text() {
      return Optional.empty();
    }

    /**
     * Returns targets of which a match must declare at least one.
     *
     * @return target filter, empty for any
     */
    default Set<Label> targets() {
      return Set.of();
    }

    /**
     * Returns artifact families of which a match must publish at least one.
     *
     * @return family filter, empty for any
     */
    default Set<Class<? extends Artifact>> families() {
      return Set.of();
    }

    /**
     * Selects catalog content by coordinates.
     *
     * @param coordinates project, release, or file coordinates
     * @return criteria with only the identifier
     */
    static Default of(@NonNull Coordinate coordinates) {
      return new Default(Optional.of(coordinates), Optional.empty(), Set.of(), Set.of(), Optional.empty());
    }

    /**
     * Searches catalog content by free text.
     *
     * @param text search text
     * @return criteria with only the text
     */
    static Default text(@NonNull String text) {
      return new Default(Optional.empty(), Optional.of(text), Set.of(), Set.of(), Optional.empty());
    }

    /**
     * Default immutable search criteria.
     *
     * @param identifier direct coordinates
     * @param text       search text
     * @param targets    target filter
     * @param families   artifact family filter
     * @param duration   provider-interpreted duration
     */
    record Default(
        @NonNull Optional<Coordinate> identifier,
        @NonNull Optional<String> text,
        @NonNull Set<Label> targets,
        @NonNull Set<Class<? extends Artifact>> families,
        @NonNull Optional<Duration> duration) implements Search {

      /**
       * Copies the filters and rejects blank text.
       */
      public Default {
        targets = Set.copyOf(targets);
        families = Set.copyOf(families);
        if (text.isPresent() && text.get().isBlank()) {
          throw new IllegalArgumentException("Search text must be non-blank when present.");
        }
      }

      /**
       * Replaces the target filter.
       *
       * @param targets targets of which a match must declare one
       * @return updated criteria
       */
      public Default withTargets(@NonNull Set<Label> targets) {
        return new Default(identifier, text, targets, families, duration);
      }

      /**
       * Replaces the family filter.
       *
       * @param families families of which a match must publish one
       * @return updated criteria
       */
      public Default withFamilies(@NonNull Set<Class<? extends Artifact>> families) {
        return new Default(identifier, text, targets, families, duration);
      }

      /**
       * Replaces the provider-interpreted duration.
       *
       * @param duration duration
       * @return updated criteria
       */
      public Default withDuration(@NonNull Duration duration) {
        return new Default(identifier, text, targets, families, Optional.of(duration));
      }
    }
  }
}
