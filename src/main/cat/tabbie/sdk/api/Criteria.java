package cat.tabbie.sdk.api;

import java.time.Duration;
import java.util.Optional;

/**
 *
 * <h1>Criteria</h1>
 *
 * @param <Identifier> Direct reference to an item
 *
 *                     <p>
 *                     Selection criteria for that a given {@link Query query}
 *                     may be applied to.
 *                     </p>
 */
public interface Criteria<Identifier> {

  Optional<Identifier> identifier();

  Optional<Duration> duration();

  static <Identifier> Criteria<Identifier> identifier(Identifier id) {
    return new Implicit<>(Optional.of(id), Optional.empty());
  }

  static <Identifier> Criteria<Identifier> duration(Duration duration) {
    return new Implicit<Identifier>(Optional.empty(), Optional.of(duration));
  }
}

record Implicit<Identifier>(
    Optional<Identifier> identifier,
    Optional<Duration> duration) implements Criteria<Identifier> {
}
