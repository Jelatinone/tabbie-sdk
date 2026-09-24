package cat.tabbie.sdk.album;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import cat.tabbie.sdk.merchant.Observer;
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
  T collapse(Context context) throws IOException;

  /**
   * Describes an already available value.
   *
   * @param <T>   result type
   * @param value available result
   * @return constant intermediate
   */
  static <T> Intermediate<T> value(T value) {
    return new Value<>(value);
  }

  /**
   * Describes a pure transformation of this result.
   *
   * @param <R>    result type
   * @param mapper transformation invoked during collapse
   * @return mapped intermediate
   */
  default <R> Intermediate<R> map(@NonNull Function<? super T, ? extends R> mapper) {
    return new Mapped<>(this, mapper);
  }

  /**
   * Describes dependent work constructed from this result.
   *
   * @param <R>    result type
   * @param mapper intermediate factory invoked during collapse
   * @return dependent intermediate
   */
  default <R> Intermediate<R> flatMap(@NonNull Function<? super T, ? extends Intermediate<R>> mapper) {
    return new Flat<>(this, mapper);
  }

  /**
   * Combines intermediates into an ordered result.
   *
   * @param <T>      element type
   * @param elements ordered intermediates
   * @return intermediate producing an immutable result list
   */
  static <T> Intermediate<List<T>> sequence(@NonNull List<? extends Intermediate<? extends T>> elements) {
    return new Sequence<>(elements);
  }

  /**
   * An already available value.
   *
   * @param <T>   result type
   * @param value result
   */
  record Value<T>(T value) implements Intermediate<T> {

    @Override
    public T collapse(Context context) {
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
  record Mapped<S, T>(
      @NonNull Intermediate<S> source,
      @NonNull Function<? super S, ? extends T> mapper) implements Intermediate<T> {

    @Override
    public T collapse(Context context) throws IOException {
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
  record Flat<S, T>(@NonNull Intermediate<S> source,
      @NonNull Function<? super S, ? extends Intermediate<T>> mapper) implements Intermediate<T> {

    @Override
    public T collapse(Context context) throws IOException {
      return mapper.apply(source.collapse(context)).collapse(context);
    }
  }

  /**
   * Ordered intermediates producing an immutable list.
   *
   * @param <T>      element type
   * @param elements ordered intermediates
   */
  record Sequence<T>(@NonNull List<? extends Intermediate<? extends T>> elements)
      implements Intermediate<List<T>> {

    /**
     * Copies intermediates without evaluating them.
     */
    public Sequence {
      elements = List.copyOf(elements);
    }

    @Override
    public List<T> collapse(Context context) throws IOException {
      List<T> results = new ArrayList<>(elements.size());
      for (Intermediate<? extends T> element : elements)
        results.add(element.collapse(context));
      return List.copyOf(results);
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
