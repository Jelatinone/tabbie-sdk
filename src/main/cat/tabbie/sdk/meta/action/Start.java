package cat.tabbie.sdk.meta.action;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import cat.tabbie.sdk.catalog.Product;
import cat.tabbie.sdk.platform.Execution;
import cat.tabbie.sdk.target.Label;
import cat.tabbie.sdk.target.Platform;
import lombok.NonNull;

/**
 * Starts a Cat: the method describes how its distribution runs, for the
 * targets its {@link cat.tabbie.sdk.meta.attribute.Applies} declarations name,
 * and runs nothing. A start acts on a distribution as a type: its
 * {@link Signature} takes only its {@link Context}, and every start is a static
 * method acting on the distribution type declaring it, or on the one its
 * {@link cat.tabbie.sdk.meta.attribute.Subject Subject} names. Exactly one
 * start applies to every target a Cat can run on.
 *
 * <p>
 * {@link Stop}, {@link Restart}, {@link Hold} and {@link Resume} act on the
 * same Cat and are given the same context. Any number of Cats may run at once,
 * so nothing a start describes may assume it is the only one running.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(value = Start.Signature.class, cardinality = Cardinality.EXACTLY_ONE)
public @interface Start {

  /**
   * The signature of every start, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Describes how a Cat runs.
     *
     * @param context the Cat's root, its target, its release and its settings
     * @return how to run it
     * @throws IOException when the start depends on a file that cannot be read
     */
    @NonNull
    Run start(@NonNull Context context) throws IOException;
  }

  /**
   * What every running action is given: its head, the Cat's root, and what the
   * Cat runs. Core implements it.
   */
  interface Context extends Action.Context {

    /**
     * Returns the target the Cat runs as.
     *
     * @return the target's label
     */
    @NonNull
    Label label();

    /**
     * Returns the platform of the machine the Cat runs on, for choosing a
     * native program.
     *
     * @return the machine's platform
     */
    @NonNull
    Platform platform();

    /**
     * Returns the release the Cat runs, whose distributables are placed where
     * the place for their type puts them, each under its source's name.
     *
     * @return the release
     */
    @NonNull
    Product.Build release();

    /**
     * Returns the memory bound the running program is given, which the start
     * interprets, such as a JVM's maximum heap.
     *
     * @return maximum memory in bytes, or empty for the program's default
     */
    @NonNull
    Optional<Long> maximumMemory();
  }

  /**
   * How a Cat runs: a process core starts and owns, or a hand-off to
   * something else.
   */
  sealed interface Run permits Managed, Unmanaged {

    /**
     * Returns what core runs to start the Cat.
     *
     * @return the execution
     */
    @NonNull
    Execution execution();
  }

  /**
   * A process core starts and owns until it exits, so it can write to its
   * input, signal it, and observe its exit.
   *
   * @param execution what core runs
   */
  record Managed(@NonNull Execution execution) implements Run {
  }

  /**
   * An execution that hands the Cat to something else, such as a service
   * manager or an outside launcher, and then exits. Core does not own what
   * runs, so only a {@link Stop.Invoke} stops it.
   *
   * @param execution what core runs to hand the Cat over
   */
  record Unmanaged(@NonNull Execution execution) implements Run {
  }
}
