package cat.tabbie.sdk.album.revision;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import cat.tabbie.sdk.album.revision.Intermediate.Step.Effect;
import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * Immutable unevaluated work that can be collapsed into an exact value when
 * supplied with its required execution context.
 *
 * Construction and inspection never invoke callbacks or perform I/O.
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
   * @return effectful intermediate
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
  }

  /**
   * Pure transformation of another intermediate. Named {@code Mapped} rather
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
  }

  /**
   * Ordered intermediates producing an immutable list.
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
      for (Intermediate<? extends T> element : elements)
        results.add(element.collapse(context));
      return List.copyOf(results);
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
  }

  /**
   * Alternatives collapsed in preference order until one succeeds. When every
   * alternative fails, the first failure is thrown with the others suppressed.
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
          } else {
            failure.addSuppressed(attempt);
          }
        }
      }
      throw failure;
    }
  }

  /**
   * A named effect-ful intermediate.
   *
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
     * One effect performed with the collapsing context.
     *
     * @param <T>    result type
     * @param action effect
     */
    record Effect<T>(@NonNull Action<T> action) implements Step<T> {

      @Override
      public T collapse(Step.Context context) throws IOException {
        return null;
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
        return Observer.NONE;
      }

      record Default(@NonNull Path scratch, @NonNull Observer<?, ?> observer) implements Context {
      }
    }
  }
}
