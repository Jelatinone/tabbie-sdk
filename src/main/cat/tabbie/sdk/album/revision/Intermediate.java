package cat.tabbie.sdk.album.revision;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import cat.tabbie.sdk.album.revision.Intermediate.Step.Effect;
import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * Immutable unevaluated work that can be collapsed into an exact value when
 * supplied with its required execution context.
 *
 * <p>
 * Construction and inspection never invoke callbacks or perform I/O. Collapsing
 * the same intermediate again repeats its effects.
 *
 * @param <T> result type
 */
public sealed interface Intermediate<T> {

	/**
	 * Collapses this intermediate into its exact value, performing any declared
	 * effects using the supplied context.
	 *
	 * @param context caller-owned execution context
	 * @return collapsed result
	 * @throws IOException when a declared effect fails
	 */
	T collapse(Step.Context context) throws IOException;

	/**
	 * Scrutinizes this intermediate into a resulting type reflecting whether this
	 * step can verifiably be performed, performing any declared effects using the
	 * supplied context's scratch path.
	 * 
	 * @param context caller-owned execution context
	 * @return scrutinized result
	 * @throws IOException when a declared effect fails
	 * 
	 * @apiNote while the scratch path should be used for intermediate work, it is
	 *          cleared after tabbie's terminal actions. Therefore, implementations
	 *          should not defer work or precompute work for
	 *          {@link #collapse(cat.tabbie.sdk.album.revision.Intermediate.Step.Context)
	 *          collapse} here.
	 */
	Scrutiny<T> scrutinize(Step.Context context) throws IOException;

	/**
	 * Resulting scrutinization type reflecting whether an {@link Intermediate} may
	 * be verifiably performed with the given context.
	 * 
	 * @param <T> result type
	 */
	sealed interface Scrutiny<T> {

		/**
		 * Result of a scrutinization
		 * 
		 * @return scrutinized value
		 */
		T result();

		/**
		 * Describes a scrutinization that passed
		 *
		 * @param <T>    result type
		 * @param result scrutinized value
		 */
		record Pass<T>(T result) implements Scrutiny<T> {

		}

		/**
		 * Describes a scrutinization that did not pass; but not necessarily caused by
		 * an error.
		 *
		 * @param <T>    result type
		 * @param result scrutinized value
		 *
		 * @apiNote composites report a failed part with {@link #Fail()}, since a
		 *          failure carries no result forward.
		 */
		record Fail<T>(T result) implements Scrutiny<T> {

			/**
			 * Describes a failure without a result.
			 */
			public Fail() {
				this(null);
			}
		}

		/**
		 * Describes a scrutinization that may not be performed without reversible
		 * side-effects, and therefore is assumed to pass successfully.
		 *
		 * @param <T>    result type
		 * @param result empty result
		 *
		 * @apiNote constructor {@link #Mock()} is preferred to {@link #Mock(Object)}.
		 */
		record Mock<T>(T result) implements Scrutiny<T> {

			/**
			 * Describes a mock without a result.
			 */
			public Mock() {
				this(null);
			}
		}
	}

	/**
	 * Describes an already available value.
	 *
	 * @param <T>   result type
	 * @param value available result
	 * @return constant intermediate
	 */
	static <T> Intermediate<T> of(T value) {
		return new Value<>(value);
	}

	/**
	 * Describes one effect performed only during collapse.
	 *
	 * @param <T>    result type
	 * @param action effect using the collapsing context
	 * @return effect-ful intermediate
	 */
	static <T> Intermediate<T> of(@NonNull Action<T> action) {
		return new Effect<>(action);
	}

	/**
	 * Describes a pure transformation of this result.
	 *
	 * @param <R>    result type
	 * @param mapper transformation invoked during collapse
	 * @return mapped intermediate
	 */
	default <R> Intermediate<R> map(@NonNull Function<? super T, ? extends R> mapper) {
		return new Transform<>(this, mapper);
	}

	/**
	 * Describes dependent work constructed from this result.
	 *
	 * @param <R>    result type
	 * @param mapper intermediate factory invoked during collapse
	 * @return dependent intermediate
	 */
	default <R> Intermediate<R> flatMap(@NonNull Function<? super T, ? extends Intermediate<R>> mapper) {
		return new Compress<>(this, mapper);
	}

	/**
	 * Describes an effect performed only after this intermediate succeeds.
	 *
	 * @param <R>    result type
	 * @param action effect using the collapsing context
	 * @return chained intermediate producing the effect's result
	 */
	default <R> Intermediate<R> then(@NonNull Action<R> action) {
		return flatMap(ignored -> of(action));
	}

	/**
	 * Describes work performed only after this intermediate succeeds.
	 *
	 * @param <R>  result type
	 * @param next subsequent work
	 * @return chained intermediate producing the subsequent result
	 */
	default <R> Intermediate<R> then(@NonNull Intermediate<R> next) {
		return flatMap(ignored -> next);
	}

	/**
	 * Combines intermediates into an ordered result.
	 *
	 * @param <T>      element type
	 * @param elements ordered intermediates
	 * @return intermediate producing an immutable result list
	 */
	static <T> Intermediate<List<T>> group(@NonNull List<? extends Intermediate<? extends T>> elements) {
		return new Cluster<>(elements);
	}

	/**
	 * Combines independent intermediates that must all succeed.
	 *
	 * @param <T>      element type
	 * @param elements independent intermediates
	 * @return intermediate producing an immutable result list in supplied order
	 */
	static <T> Intermediate<List<T>> all(@NonNull List<? extends Intermediate<? extends T>> elements) {
		return new All<>(elements);
	}

	/**
	 * Combines alternatives of which one must succeed, such as mirrors.
	 *
	 * @param <T>          result type
	 * @param alternatives nonempty alternatives in preference order
	 * @return intermediate producing the first successful result
	 */
	static <T> Intermediate<T> any(@NonNull List<? extends Intermediate<? extends T>> alternatives) {
		return new Any<>(alternatives);
	}

	/**
	 * An effect performed with the collapsing context.
	 *
	 * @param <T> result type
	 */
	@FunctionalInterface
	interface Action<T> {

		/**
		 * Performs the effect.
		 *
		 * @param context collapsing context
		 * @return effect result
		 * @throws IOException when the effect fails
		 */
		T run(@NonNull Step.Context context) throws IOException;
	}

	/**
	 * An already available value.
	 *
	 * @param <T>   result type
	 * @param value result
	 */
	record Value<T>(T value) implements Intermediate<T> {

		@Override
		public T collapse(Step.Context context) {
			return value;
		}

		@Override
		public Scrutiny<T> scrutinize(Step.Context context) {
			// A single value must necessarily pass...
			return new Scrutiny.Pass<T>(value);
		}
	}

	/**
	 * Pure transformation of another intermediate. Named {@code Transform} rather
	 * than {@code Map} so implementations of {@link Step} that also need
	 * {@code java.util.Map} are not shadowed by this member type.
	 *
	 * @param <S>    input type
	 * @param <T>    result type
	 * @param source prerequisite
	 * @param mapper transformation
	 */
	record Transform<S, T>(
			@NonNull Intermediate<S> source,
			@NonNull Function<? super S, ? extends T> mapper) implements Intermediate<T> {

		@Override
		public T collapse(Step.Context context) throws IOException {
			return mapper.apply(source.collapse(context));
		}

		@Override
		public Scrutiny<T> scrutinize(Step.Context context) throws IOException {
			return switch (source.scrutinize(context)) {
				case Scrutiny.Pass<S> pass -> new Scrutiny.Pass<>(mapper.apply(pass.result()));
				case Scrutiny.Fail<S> _ -> new Scrutiny.Fail<>();
				case Scrutiny.Mock<S> _ -> new Scrutiny.Mock<>();
			};
		}
	}

	/**
	 * Dependent work whose next intermediate is determined by the source result.
	 *
	 * @param <S>    input type
	 * @param <T>    result type
	 * @param source prerequisite
	 * @param mapper dependent intermediate factory
	 */
	record Compress<S, T>(@NonNull Intermediate<S> source,
			@NonNull Function<? super S, ? extends Intermediate<T>> mapper) implements Intermediate<T> {

		@Override
		public T collapse(Step.Context context) throws IOException {
			return mapper.apply(source.collapse(context)).collapse(context);
		}

		@Override
		public Scrutiny<T> scrutinize(Step.Context context) throws IOException {
			return switch (source.scrutinize(context)) {
				case Scrutiny.Pass<S> pass -> mapper.apply(pass.result()).scrutinize(context);
				// Dependent work cannot be built without the source's result
				case Scrutiny.Fail<S> _ -> new Scrutiny.Fail<>();
				case Scrutiny.Mock<S> _ -> new Scrutiny.Mock<>();
			};
		}
	}

	/**
	 * Ordered intermediates producing an immutable list. Scrutinizing stops at the
	 * first failing element, and mocks when any element mocked.
	 *
	 * @param <T>      element type
	 * @param elements ordered intermediates
	 */
	record Cluster<T>(@NonNull List<? extends Intermediate<? extends T>> elements)
			implements Intermediate<List<T>> {

		/**
		 * Copies intermediates without evaluating them.
		 */
		public Cluster {
			elements = List.copyOf(elements);
		}

		@Override
		public List<T> collapse(Step.Context context) throws IOException {
			List<T> results = new ArrayList<>(elements.size());
			for (Intermediate<? extends T> element : elements) {
				results.add(element.collapse(context));
			}
			// Results may be null, as Intermediate.of(null) is
			return Collections.unmodifiableList(results);
		}

		@Override
		public Scrutiny<List<T>> scrutinize(Step.Context context) throws IOException {
			List<T> results = new ArrayList<>(elements.size());
			boolean mocked = false;
			for (Intermediate<? extends T> element : elements) {
				switch (element.scrutinize(context)) {
					case Scrutiny.Pass<? extends T> pass -> results.add(pass.result());
					case Scrutiny.Fail<? extends T> _ -> {
						return new Scrutiny.Fail<>();
					}
					case Scrutiny.Mock<? extends T> _ -> mocked = true;
				}
			}
			return mocked ? new Scrutiny.Mock<>() : new Scrutiny.Pass<>(Collections.unmodifiableList(results));
		}
	}

	/**
	 * Independent intermediates that must all succeed. This implementation
	 * collapses them one after another and stops at the first failure; an
	 * interpreter may collapse them concurrently against a thread-safe context.
	 *
	 * @param <T>      element type
	 * @param elements independent intermediates
	 */
	record All<T>(@NonNull List<? extends Intermediate<? extends T>> elements)
			implements Intermediate<List<T>> {

		/**
		 * Copies intermediates without evaluating them.
		 */
		public All {
			elements = List.copyOf(elements);
		}

		@Override
		public List<T> collapse(@NonNull Step.Context context) throws IOException {
			return new Cluster<T>(elements).collapse(context);
		}

		@Override
		public Scrutiny<List<T>> scrutinize(@NonNull Step.Context context) throws IOException {
			return new Cluster<T>(elements).scrutinize(context);
		}
	}

	/**
	 * Alternatives collapsed in preference order until one succeeds. When every
	 * alternative fails, the first failure is thrown with the others suppressed.
	 *
	 * <p>
	 * Scrutinizing returns the first alternative that passes or mocks. When none
	 * does, it fails if any alternative failed without throwing, and otherwise
	 * throws as collapsing does.
	 *
	 * @param <T>          result type
	 * @param alternatives nonempty alternatives in preference order
	 */
	record Any<T>(@NonNull List<? extends Intermediate<? extends T>> alternatives) implements Intermediate<T> {

		/**
		 * Copies alternatives without evaluating them.
		 *
		 * @throws IllegalArgumentException when no alternative is supplied
		 */
		public Any {
			alternatives = List.copyOf(alternatives);
			if (alternatives.isEmpty()) {
				throw new IllegalArgumentException("At least one alternative is required.");
			}
		}

		@Override
		public T collapse(@NonNull Step.Context context) throws IOException {
			IOException failure = null;
			for (Intermediate<? extends T> alternative : alternatives) {
				try {
					return alternative.collapse(context);
				} catch (IOException attempt) {
					if (failure == null) {
						failure = attempt;
					} else if (attempt != failure) {
						failure.addSuppressed(attempt);
					}
				}
			}
			throw failure;
		}

		@Override
		public Scrutiny<T> scrutinize(@NonNull Step.Context context) throws IOException {
			IOException failure = null;
			boolean failed = false;
			for (Intermediate<? extends T> alternative : alternatives) {
				try {
					switch (alternative.scrutinize(context)) {
						case Scrutiny.Pass<? extends T> pass -> {
							return new Scrutiny.Pass<>(pass.result());
						}
						case Scrutiny.Mock<? extends T> _ -> {
							return new Scrutiny.Mock<>();
						}
						case Scrutiny.Fail<? extends T> _ -> failed = true;
					}
				} catch (IOException attempt) {
					if (failure == null) {
						failure = attempt;
					} else if (attempt != failure) {
						failure.addSuppressed(attempt);
					}
				}
			}
			if (failed) {
				return new Scrutiny.Fail<>();
			}
			// Every alternative threw
			throw failure;
		}
	}

	/**
	 * A named effectful intermediate.
	 *
	 * <p>
	 * The step itself implements its effect through
	 * {@link #collapse(Context)}. The marker exposes the operation as a
	 * meaningful effect boundary without requiring a separate evaluator.
	 *
	 * @param <T> result type
	 */
	non-sealed interface Step<T> extends Intermediate<T> {

		/**
		 * Identifies the effect independently of its implementation class.
		 *
		 * @return namespaced operation name
		 */
		default String name() {
			Class<?> clazz = getClass();
			Class<?> enclosing = clazz.getEnclosingClass();
			return enclosing != null
					? String.format("%s:%s", enclosing.getSimpleName(), clazz.getSimpleName())
					: clazz.getSimpleName();
		}

		/**
		 * Mocks this step without performing it, since its effects may reach beyond
		 * scratch space.
		 *
		 * @param context caller-owned execution context
		 * @return mock of this step
		 * @throws IOException when an overriding step's check fails
		 *
		 * @apiNote steps whose effects stay within scratch space and the retention
		 *          backend override this to return
		 *          {@code new Scrutiny.Pass<>(collapse(context))}; steps with other
		 *          effects override it to check their preconditions.
		 */
		@Override
		default Scrutiny<T> scrutinize(Step.Context context) throws IOException {
			return new Scrutiny.Mock<>();
		}

		/**
		 * One effect performed with the collapsing context. Scrutinizing it mocks,
		 * since a bare {@link Action} cannot be checked without running it.
		 *
		 * @param <T>    result type
		 * @param action effect
		 */
		record Effect<T>(@NonNull Action<T> action) implements Step<T> {

			@Override
			public T collapse(Step.Context context) throws IOException {
				return action.run(context);
			}
		}

		/**
		 * Caller-owned capabilities available while collapsing work.
		 */
		interface Context {

			/**
			 * Supplies an existing execution-owned directory outside managed targets
			 * for temporary files.
			 *
			 * @return scratch directory
			 */
			Path scratch();

			/**
			 * Supplies an observer of intermediate steps.
			 *
			 * @return context observer
			 */
			default Observer<?, ?> observer() {
				return Observer.none();
			}

			/**
			 * Immutable execution context.
			 *
			 * @param scratch  existing execution-owned scratch directory
			 * @param observer observer of intermediate steps
			 */
			record Default(@NonNull Path scratch, @NonNull Observer<?, ?> observer) implements Context {
			}
		}
	}
}
