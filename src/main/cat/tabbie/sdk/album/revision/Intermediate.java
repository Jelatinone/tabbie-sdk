package cat.tabbie.sdk.album.revision;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import cat.tabbie.sdk.album.model.Plate;
import cat.tabbie.sdk.album.revision.Intermediate.Step.Effect;
import cat.tabbie.sdk.platform.layout.Violation;
import lombok.NonNull;

/**
 * Immutable unevaluated work that collapses into a checked result when
 * supplied with a {@link Plate}.
 *
 * <p>
 * Construction and inspection never invoke callbacks or perform I/O. Collapsing
 * the same intermediate again repeats its effects. Work reaches its targets
 * only through the plate, so collapsing it against a rehearsal's plate checks
 * it without changing anything outside scratch space, and collapsing it against
 * an acting plate carries it out.
 *
 * @param <Result> result type
 */
public sealed interface Intermediate<Result> {

  /**
   * Collapses this intermediate, performing its declared effects through the
   * plate and checking them as it goes.
   *
   * @param plate caller-owned plate the work is carried out on
   * @return pass carrying the result, or failure when a check ran and refused
   * @throws IOException when an effect fails or a check cannot be made
   */
  Scrutiny<Result> collapse(@NonNull Plate plate) throws IOException;

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
   * @param action effect using the collapsing plate
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
  default <R> Intermediate<R> map(@NonNull Function<? super Result, ? extends R> mapper) {
    return new Transform<>(this, mapper);
  }

  /**
   * Describes dependent work constructed from this result.
   *
   * @param <R>    result type
   * @param mapper intermediate factory invoked during collapse
   * @return dependent intermediate
   */
  default <R> Intermediate<R> flatMap(@NonNull Function<? super Result, ? extends Intermediate<R>> mapper) {
    return new Compress<>(this, mapper);
  }

  /**
   * Describes an effect performed only after this intermediate passes.
   *
   * @param <R>    result type
   * @param action effect using the collapsing plate
   * @return chained intermediate producing the effect's result
   */
  default <R> Intermediate<R> then(@NonNull Action<R> action) {
    return flatMap(ignored -> of(action));
  }

  /**
   * Describes work performed only after this intermediate passes.
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
   * Combines alternatives of which one must pass, such as mirrors.
   *
   * @param <T>          result type
   * @param alternatives nonempty alternatives in preference order
   * @return intermediate producing the first passing result
   */
  static <T> Intermediate<T> any(@NonNull List<? extends Intermediate<? extends T>> alternatives) {
    return new Any<>(alternatives);
  }

  /**
   * An effect performed with the collapsing plate.
   *
   * @param <T> result type
   */
  @FunctionalInterface
  interface Action<T> {

    /**
     * Performs the effect.
     *
     * @param plate collapsing plate
     * @return effect result
     * @throws IOException when the effect fails
     */
    T run(@NonNull Plate plate) throws IOException;
  }

  /**
   * An already available value, which always passes.
   *
   * @param <T>   result type
   * @param value result
   */
  record Value<T>(T value) implements Intermediate<T> {

    @Override
    public Scrutiny<T> collapse(@NonNull Plate plate) {
      return new Scrutiny.Pass<>(value);
    }
  }

  /**
   * Pure transformation of another intermediate's passing result; a failure
   * passes through with its cause. Named {@code Transform} rather than
   * {@code Map} so implementations of {@link Step} that also need
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
    public Scrutiny<T> collapse(@NonNull Plate plate) throws IOException {
      return switch (source.collapse(plate)) {
        case Scrutiny.Pass<S> pass -> new Scrutiny.Pass<>(mapper.apply(pass.result()));
        case Scrutiny.Fail<S> fail -> new Scrutiny.Fail<>(null, fail.failure());
      };
    }
  }

  /**
   * Dependent work whose next intermediate is built from the source's passing
   * result; a failure passes through with its cause, without building it.
   *
   * @param <S>    input type
   * @param <T>    result type
   * @param source prerequisite
   * @param mapper dependent intermediate factory
   */
  record Compress<S, T>(@NonNull Intermediate<S> source,
      @NonNull Function<? super S, ? extends Intermediate<T>> mapper) implements Intermediate<T> {

    @Override
    public Scrutiny<T> collapse(@NonNull Plate plate) throws IOException {
      return switch (source.collapse(plate)) {
        case Scrutiny.Pass<S> pass -> mapper.apply(pass.result()).collapse(plate);
        // Dependent work cannot be built without the source's result
        case Scrutiny.Fail<S> fail -> new Scrutiny.Fail<>(null, fail.failure());
      };
    }
  }

  /**
   * Ordered intermediates producing an immutable list. Collapsing stops at the
   * first element that fails, and fails with its cause.
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
    public Scrutiny<List<T>> collapse(@NonNull Plate plate) throws IOException {
      List<T> results = new ArrayList<>(elements.size());
      for (Intermediate<? extends T> element : elements) {
        switch (element.collapse(plate)) {
          case Scrutiny.Pass<? extends T> pass -> results.add(pass.result());
          case Scrutiny.Fail<? extends T> fail -> {
            return new Scrutiny.Fail<>(null, fail.failure());
          }
        }
      }
      // Results may be null, as Intermediate.of(null) is
      return new Scrutiny.Pass<>(Collections.unmodifiableList(results));
    }
  }

  /**
   * Alternatives collapsed in preference order until one passes. When none
   * passes, it fails if any alternative failed without throwing; when every
   * alternative threw, the first failure is thrown with the others suppressed.
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
    public Scrutiny<T> collapse(@NonNull Plate plate) throws IOException {
      IOException failure = null;
      boolean failed = false;
      for (Intermediate<? extends T> alternative : alternatives) {
        try {
          switch (alternative.collapse(plate)) {
            case Scrutiny.Pass<? extends T> pass -> {
              return new Scrutiny.Pass<>(pass.result());
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
   * The step itself implements its effect through {@link #collapse(Plate)},
   * reaching its targets only through the plate. Effects that nothing later
   * reads back, such as starting a process, are not performed by the step: it
   * returns a description of them, which whoever acts carries out.
   *
   * @param <T> result type
   */
  non-sealed interface Step<T> extends Intermediate<T> {

    /**
     * Describes this step for display, such as while it is rehearsed or acted
     * on. Defaults to its namespaced type name; steps override it to say what
     * they do with their own inputs, such as the archive they read.
     *
     * @return one-line description of the step
     */
    default String name() {
      Class<?> clazz = getClass();
      Class<?> enclosing = clazz.getEnclosingClass();
      return enclosing != null
          ? String.format("%s:%s", enclosing.getSimpleName(), clazz.getSimpleName())
          : clazz.getSimpleName();
    }

    /**
     * One effect performed with the collapsing plate, passing with its result.
     * An effect the plate's tree does not allow, such as an image described
     * where the tree forbids it, fails with the tree's violation rather than
     * throwing it.
     *
     * @param <T>    result type
     * @param action effect
     */
    record Effect<T>(@NonNull Action<T> action) implements Step<T> {

      @Override
      public Scrutiny<T> collapse(@NonNull Plate plate) throws IOException {
        try {
          return new Scrutiny.Pass<>(action.run(plate));
        } catch (Violation violation) {
          return new Scrutiny.Fail<>(null, violation);
        }
      }
    }
  }
}
