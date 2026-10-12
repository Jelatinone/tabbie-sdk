package cat.tabbie.sdk.api;

import java.util.Collection;
import java.util.Optional;

import cat.tabbie.sdk.album.revision.Intermediate;
import lombok.NonNull;

/**
 * A local or remote read capability. Queries are deferred: each returns an
 * {@link Intermediate} that reads only when collapsed, so the caller decides
 * where and when the I/O happens. A request that cannot be answered fails the
 * collapse with an {@link java.io.IOException}; failures are never disguised
 * as empty results. An empty collection or an empty {@link Optional} means
 * nothing matched.
 *
 * @param <Criterion> provider-specific selection criteria
 * @param <Result>    returned item type
 */
@FunctionalInterface
public interface Queryable<Criterion, Result> {

  /**
   * Determines whether any item matches.
   *
   * @param query existence request
   * @return unevaluated read producing whether any item matches
   *
   * @apiNote The default requests at most one item through
   *          {@link #query(cat.tabbie.sdk.api.Query.Several) several}.
   */
  default Intermediate<Boolean> query(@NonNull Query.Exists<Criterion> query) {
    return query(new Query.Several<>(query.criteria(), 1)).map(results -> !results.isEmpty());
  }

  /**
   * Counts every matching item.
   *
   * @param query count request
   * @return unevaluated read producing the count, including zero
   *
   * @apiNote The default fetches every match through
   *          {@link #query(cat.tabbie.sdk.api.Query.Several) several}; remote
   *          providers should override it with a native count.
   */
  default Intermediate<Long> query(@NonNull Query.Count<Criterion> query) {
    return query(new Query.Several<>(query.criteria())).map(results -> (long) results.size());
  }

  /**
   * Requests the first matching item in the provider's documented order.
   *
   * @param query singular request
   * @return unevaluated read producing the item, or empty when none matches
   *
   * @apiNote The default requests at most one item through
   *          {@link #query(cat.tabbie.sdk.api.Query.Several) several}.
   */
  default Intermediate<Optional<Result>> query(@NonNull Query.Singular<Criterion> query) {
    return query(new Query.Several<>(query.criteria(), 1)).map(results -> results.stream().findFirst());
  }

  /**
   * Requests matching items in the provider's documented order, honoring the
   * request's limit.
   *
   * @param query collection request
   * @return unevaluated read producing the matching items, empty when none match
   */
  Intermediate<Collection<Result>> query(@NonNull Query.Several<Criterion> query);
}
