package cat.tabbie.sdk.api;

import java.util.Collection;
import java.util.Optional;

import lombok.NonNull;

/**
 * A local or remote read capability. Implementations return non-null containers
 * and distinguish an absent item from a failed request; failures must not be
 * disguised as empty results.
 *
 * @param <Criterion> provider-specific selection criteria
 * @param <Result>    returned item type
 */
@FunctionalInterface
public interface Queryable<Criterion, Result> {
  /**
   * Derives existence from the count unless overridden.
   *
   * @param query existence request
   * @return true or false when known, or empty when the count is unavailable
   *
   * @apiNote Default implementation always opportunistically determines existence
   *          found via {@link #query(cat.tabbie.sdk.api.Query.Count) count}
   *          implementation.
   */
  default Optional<Boolean> query(@NonNull Query.Exists<Criterion> query) {
    return query(new Query.Count<>(query.criteria())).map(v -> v > 0L);
  }

  /**
   * Counts all matching results by default. Remote providers may override this
   * to avoid fetching every result.
   *
   * @param query count request
   * @return known count including zero, or empty when unavailable
   *
   * @apiNote Default implementation always opportunistically takes counts results
   *          found via {@link #query(cat.tabbie.sdk.api.Query.Several) several}
   *          implementation.
   */
  default Optional<Long> query(@NonNull Query.Count<Criterion> query) {
    return query(new Query.Several<>(query.criteria())).map(c -> (long) c.size());
  }

  /**
   * Requests one item; providers document ordering when several items match.
   *
   * @param query singular request
   * @return selected item, or empty when none matches
   *
   * @apiNote Default implementation always opportunistically takes first result
   *          found via {@link #query(cat.tabbie.sdk.api.Query.Several) several}
   *          implementation.
   */
  default Optional<Result> query(Query.Singular<Criterion> query) {
    return query(new Query.Several<>(query.criteria())).flatMap(c -> c.stream().findFirst());
  }

  /**
   * Requests a collection honoring the explicit limit, if present.
   *
   * @param query collection request
   * @return matching items, empty when none match
   */
  Optional<Collection<Result>> query(Query.Several<Criterion> query);
}
