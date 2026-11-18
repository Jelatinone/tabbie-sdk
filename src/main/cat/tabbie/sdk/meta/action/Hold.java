package cat.tabbie.sdk.meta.action;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lombok.NonNull;

/**
 * Holds a running Cat's saving, so its files can be copied whole: the method
 * describes the console steps that pause it, for the targets its
 * {@link cat.tabbie.sdk.meta.attribute.Applies} declarations name, such as
 * Java's {@code save-off} and {@code save-all flush}, or Bedrock's
 * {@code save hold} and {@code save query}. Like a {@link Start start}, it acts
 * on a distribution as a type and is given the start's
 * {@link Start.Context context}. Core snapshots a running Cat between a hold and
 * its {@link Resume resume}; a distribution with no hold is snapshotted only
 * while stopped. At most one applies to a target.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(value = Hold.Signature.class, cardinality = Cardinality.ONE)
public @interface Hold {

  /**
   * The signature of every hold, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Describes how a running Cat's saving pauses.
     *
     * @param context the Cat's root, its target, its release and its settings
     * @return the console steps, in order, ending once the files are safe to
     *         copy
     * @throws IOException when the hold depends on a file that cannot be read
     */
    @NonNull
    List<Step> hold(@NonNull Start.Context context) throws IOException;
  }

  /**
   * One step of a conversation with a running Cat's console, which core
   * carries out in order: a line written to its input, or a wait for a line
   * of its output.
   */
  sealed interface Step permits Send, Await {
  }

  /**
   * A line written to the console's input.
   *
   * @param line the line, without its ending
   */
  record Send(@NonNull String line) implements Step {
  }

  /**
   * A wait for a line of the console's output to match a pattern, such as the
   * server reporting that it saved. A wait that runs out fails the hold, and
   * core resumes the Cat.
   *
   * @param pattern regular expression a line of output contains a match of
   * @param timeout how long to wait
   */
  record Await(@NonNull String pattern, @NonNull Duration timeout) implements Step {

    /**
     * Checks the pattern is a regular expression and the timeout is positive.
     *
     * @throws IllegalArgumentException when the pattern does not compile, or the
     *                                  timeout is not positive
     */
    public Await {
      try {
        Pattern.compile(pattern);
      } catch (PatternSyntaxException exception) {
        throw new IllegalArgumentException("A wait's pattern is a regular expression.", exception);
      }
      if (timeout.isNegative() || timeout.isZero()) {
        throw new IllegalArgumentException("A wait's timeout is positive.");
      }
    }
  }
}
