package cat.tabbie.sdk.api;

import java.time.Duration;
import java.util.Optional;

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
