package cat.tabbie.sdk.api;

import java.time.Duration;
import java.util.Optional;

import lombok.NonNull;

/**
 * <h2>Criteria</h2>
 *
 * Selection inputs interpreted by a provider. A duration's meaning, such as a
 * time window, belongs to the concrete provider; this interface does not impose
 * timeout or caching behavior.
 *
 * @param <Identifier> direct item identity type
 */
public interface Criteria<Identifier> {

	/**
	 * Optional direct item identity
	 *
	 * @return item identity
	 */
	Optional<Identifier> identifier();

	/**
	 * optional provider query duration (TTL)
	 *
	 * @return query duration
	 */
	Optional<Duration> duration();

	/**
	 * Selects an item by identity.
	 *
	 * @param <Identifier> identity type
	 * @param id           item identity
	 * @return criteria containing only the identity
	 */
	static <Identifier> Criteria<Identifier> identifier(@NonNull Identifier id) {
		return new Implicit<>(Optional.of(id), Optional.empty());
	}

	/**
	 * Supplies a duration for a provider to interpret.
	 *
	 * @param <Identifier> identity type
	 * @param duration     provider-defined duration
	 * @return criteria containing only the duration
	 */
	static <Identifier> Criteria<Identifier> duration(@NonNull Duration duration) {
		return new Implicit<>(Optional.empty(), Optional.of(duration));
	}
}

/**
 * Default immutable criteria value.
 *
 * @param <Identifier> identity type
 * @param identifier   optional direct selection
 * @param duration     optional provider-defined duration
 */
record Implicit<Identifier>(@NonNull Optional<Identifier> identifier,
		@NonNull Optional<Duration> duration) implements Criteria<Identifier> {
}
