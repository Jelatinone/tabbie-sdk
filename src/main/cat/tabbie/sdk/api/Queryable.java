package cat.tabbie.sdk.api;

import java.util.Collection;
import java.util.Optional;

/**
 * 
 * <h1>Queryable</h1>
 * 
 * @param <Criterion> selection criteria
 * @param <Result>    resulting type
 * 
 *                    <p>
 *                    A resource that may be queried across any kind
 *                    of network based on selection criteria for an item with a
 *                    resulting shape.
 *                    </p>
 * 
 */
public interface Queryable<Criterion, Result> {

	default Optional<Boolean> query(Query.Exists<Criterion> query) {
		return query(new Query.Count<>(query.criteria())).map(value -> value > 0);
	}

	default Optional<Long> query(Query.Count<Criterion> query) {
		return Optional.of((long) query(new Query.Several<>(query.criteria())).size());
	}

	Optional<Result> query(Query.Singular<Criterion> query);

	Collection<Result> query(Query.Several<Criterion> query);
}