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
 * Stops a running Cat: the method describes how its distribution stops, for
 * the targets its {@link cat.tabbie.sdk.meta.attribute.Applies} declarations
 * name. Like a {@link Start start}, it acts on a distribution as a type, is
 * given the start's {@link Start.Context context}, and exactly one applies to
 * every target a Cat can run on. A {@link Input line} or a {@link Signal signal}
 * reaches only a process core owns, so it pairs only with a
 * {@link Start.Managed managed} start; core refuses other pairings.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(value = Stop.Signature.class, cardinality = Cardinality.EXACTLY_ONE)
public @interface Stop {

  /**
   * The signature of every stop, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Describes how a running Cat stops.
     *
     * @param context the Cat's root, its target, its release and its settings
     * @return how to stop it
     * @throws IOException when the stop depends on a file that cannot be read
     */
    @NonNull
    Way stop(@NonNull Start.Context context) throws IOException;
  }

  /**
   * How a Cat stops, and how long it is given to.
   */
  sealed interface Way permits Input, Signal, Invoke {

    /**
     * Returns how long core waits for the Cat to stop. A process core owns
     * that still runs afterwards is destroyed; a stop execution still running
     * afterwards fails the stop.
     *
     * @return the grace period
     */
    @NonNull
    Duration grace();
  }

  /**
   * A line written to the process's input, such as {@code stop} for a
   * Minecraft server.
   *
   * @param line  the line, without its ending
   * @param grace time allowed for the process to exit
   */
  record Input(@NonNull String line, @NonNull Duration grace) implements Way {
  }

  /**
   * A request that the process end gracefully, as {@code SIGTERM} makes.
   *
   * @param grace time allowed for the process to exit
   */
  record Signal(@NonNull Duration grace) implements Way {
  }

  /**
   * A separate execution that stops the Cat, such as a service manager's stop.
   * For a process core owns, core then waits for it to exit; otherwise, for the
   * execution to.
   *
   * @param execution what core runs to stop the Cat
   * @param grace     time allowed for the Cat to stop
   */
  record Invoke(@NonNull Execution execution, @NonNull Duration grace) implements Way {
  }
}
