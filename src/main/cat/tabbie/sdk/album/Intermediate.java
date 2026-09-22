package cat.tabbie.sdk.album;

import java.util.List;
import java.util.function.Function;

import lombok.NonNull;

/**
 * Immutable unevaluated work. Construction and inspection never invoke
 * callbacks or perform I/O. Java callbacks are process-local, not a persistence
 * format.
 *
 * Evaluators visit dependencies first, memoize by node identity within one run,
 * stop dependent work on failure, and retain completed effect outcomes. There
 * is
 * no global cache or exactly-once guarantee across retries. Core owns
 * scheduling,
 * Command receipts and recovery. Dynamic dependencies remain unresolved during
 * inspection until their factory is explicitly evaluated.
 *
 * @param <T> result type
 */
public sealed interface Intermediate<T> {

	/**
	 * Collapses the state of this intermediate into an exact value
	 * 
	 * @return collapsed resulting value
	 */
	T collapse();

	/**
	 * Describes an available value.
	 *
	 * @param <T>   result type
	 * @param value available result
	 * @return constant recipe
	 */
	static <T> Intermediate<T> value(T value) {
		return new Value<>(value);
	}

	/**
	 * Describes a pure transformation. I/O belongs in an explicit Step.
	 *
	 * @param <R>    result type
	 * @param mapper transformation invoked only during evaluation
	 * @return mapped recipe
	 */
	default <R> Intermediate<R> map(Function<? super T, ? extends R> mapper) {
		return new Map<>(this, mapper);
	}

	/**
	 * Describes dependent preparation. The mapper constructs work, never runs it.
	 *
	 * @param <R>    result type
	 * @param mapper pure recipe factory
	 * @return dynamically dependent recipe
	 */
	default <R> Intermediate<R> flatMap(Function<? super T, ? extends Intermediate<R>> mapper) {
		return new Flat<>(this, mapper);
	}

	/**
	 * Combines recipes in explicit order without evaluating them.
	 *
	 * @param <T>      element type
	 * @param elements ordered recipes
	 * @return recipe for an immutable result list
	 */
	static <T> Intermediate<List<T>> sequence(List<? extends Intermediate<? extends T>> elements) {
		return new Sequence<>(List.copyOf(elements));
	}

	/**
	 * An available value.
	 *
	 * @param <T>   result type
	 * @param value result
	 */
	record Value<T>(T value) implements Intermediate<T> {
		@Override
		public T collapse() {
			return value();
		}
	}

	/**
	 * Pure transformation.
	 *
	 * @param <S>    input type
	 * @param <T>    result type
	 * @param source prerequisite
	 * @param mapper transformation
	 */
	record Map<S, T>(@NonNull Intermediate<S> source,
			@NonNull Function<? super S, ? extends T> mapper) implements Intermediate<T> {
		@Override
		public T collapse() {
			return mapper.apply(source().collapse());
		}
	}

	/**
	 * Dynamic preparation whose remaining dependencies are unresolved.
	 *
	 * @param <S>    input type
	 * @param <T>    result type
	 * @param source prerequisite
	 * @param mapper dependent recipe factory
	 */
	record Flat<S, T>(
			@NonNull Intermediate<S> source,
			@NonNull Function<? super S, ? extends Intermediate<T>> mapper)
			implements Intermediate<T> {
		@Override
		public T collapse() {
			return mapper.apply(source().collapse()).collapse();
		}
	}

	/**
	 * Ordered prerequisites producing an immutable list.
	 *
	 * @param <T>      element type
	 * @param elements ordered recipes
	 */
	record Sequence<T>(
			@NonNull List<Intermediate<? extends T>> elements)
			implements Intermediate<List<T>> {

		/**
		 * Copies recipes without evaluation.
		 */
		public Sequence {
			elements = List.copyOf(elements);
		}

		@Override
		public List<T> collapse() {
			return elements.stream()
					.map((intermediate) -> (T) intermediate.collapse())
					.toList();
		}
	}
}