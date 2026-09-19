package cat.tabbie.sdk.api;

import java.util.Collection;
import java.util.Optional;

import lombok.NonNull;

/**
 * <h2>Queryable</h2>
 * 
 * A local or remote read capability. Implementations return non-null containers
 * and distinguish an absent item from a failed request; failures must not be
 * disguised as empty results.
 *
 * @param <Criterion> provider-specific selection criteria
 * @param <Result>    returned item type
 */
public interface Queryable<Criterion, Result> {
	/**
	 * Derives existence from the count unless overridden.
	 *
	 * @param query existence request
	 * @return true or false when known, or empty when the count is unavailable
	 */
	default Optional<Boolean> query(@NonNull Query.Exists<Criterion> query) {
		return query(new Query.Count<>(query.criteria())).map(value -> value > 0);
	}

	/**
	 * Counts all matching results by default. Remote providers may override this
	 * to avoid fetching every result.
	 *
	 * @param query count request
	 * @return known count including zero, or empty when unavailable
	 */
	default Optional<Long> query(@NonNull Query.Count<Criterion> query) {
		return Optional.of((long) query(new Query.Several<>(query.criteria())).size());
	}

	/**
	 * Requests one item; providers document ordering when several items match.
	 *
	 * @param query singular request
	 * @return selected item, or empty when none matches
	 */
	Optional<Result> query(Query.Singular<Criterion> query);

	/**
	 * Requests a collection honoring the explicit limit, if present.
	 *
	 * @param query collection request
	 * @return matching items, empty when none match
	 */
	Collection<Result> query(Query.Several<Criterion> query);
}
