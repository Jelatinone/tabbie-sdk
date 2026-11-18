package cat.tabbie.sdk.meta.action;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

import cat.tabbie.sdk.platform.Execution;
import lombok.NonNull;

/**
 * Restarts a running Cat in place, for a distribution that can: the method
 * describes how, for the targets its
 * {@link cat.tabbie.sdk.meta.attribute.Applies} declarations name. Like a
 * {@link Start start}, it acts on a distribution as a type and is given the
 * start's {@link Start.Context context}. At most one applies to a target; where
 * none does, core restarts a Cat by {@link Stop stopping} and starting it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(value = Restart.Signature.class, cardinality = Cardinality.ONE)
public @interface Restart {

  /**
   * The signature of every restart, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Describes how a running Cat restarts in place.
     *
     * @param context the Cat's root, its target, its release and its settings
     * @return how to restart it
     * @throws IOException when the restart depends on a file that cannot be
     *                     read
     */
    @NonNull
    Way restart(@NonNull Start.Context context) throws IOException;
  }

  /**
   * How a Cat restarts in place, and how long it is given to.
   */
  sealed interface Way permits Input, Invoke {

    /**
     * Returns how long core waits for the Cat to run again.
     *
     * @return the grace period
     */
    @NonNull
    Duration grace();
  }

  /**
   * A line written to the input of a process core owns, after which it runs
   * again.
   *
   * @param line  the line, without its ending
   * @param grace time allowed for the restart
   */
  record Input(@NonNull String line, @NonNull Duration grace) implements Way {
  }

  /**
   * A separate execution that restarts the Cat, such as a service manager's
   * restart.
   *
   * @param execution what core runs to restart the Cat
   * @param grace     time allowed for the restart
   */
  record Invoke(@NonNull Execution execution, @NonNull Duration grace) implements Way {
  }
}
