package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.target.Environment;
import lombok.NonNull;

/**
 * The environments an action applies to, such as only dedicated servers.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Environments {

  /**
   * Returns the environments the action applies to.
   *
   * @return at least one environment
   */
  Environment[] value();

  /**
   * What an {@link Environments} attribute says, read and checked.
   *
   * @param environments environments, empty for every environment
   */
  record Meta(@NonNull Set<Environment> environments) implements Condition {

    /**
     * Every environment, as an action without the attribute applies to.
     */
    public static final Meta EVERY = new Meta(Set.of());

    /**
     * Copies the environments.
     */
    public Meta {
      environments = Set.copyOf(environments);
    }

    /**
     * Reads what an attribute says.
     *
     * @param environments attribute as written
     * @return its environments
     * @throws IllegalArgumentException when it names none
     */
    public static Meta of(@NonNull Environments environments) {
      if (environments.value().length == 0) {
        throw new IllegalArgumentException("@Environments names nothing; leave it out to apply to every "
            + "environment.");
      }
      return new Meta(Arrays.stream(environments.value()).collect(Collectors.toSet()));
    }

    /**
     * Whether an environment is among these.
     *
     * @param environment environment, or empty for one no contribution names
     * @return whether this applies to every environment, or names it
     */
    public boolean includes(@NonNull Optional<Environment> environment) {
      return environments.isEmpty() || environment.filter(environments::contains).isPresent();
    }

    /**
     * Whether a target's environment is among these.
     *
     * @param target the target a contribution may be chosen for
     * @return whether this applies to every environment, or names the
     *         target's
     */
    @Override
    public boolean appliesTo(@NonNull Conditions.Target target) {
      return appliesTo(target.environment());
    }

    /**
     * Whether an environment is among these.
     *
     * @param environment an environment
     * @return whether this applies to every environment, or names it
     */
    public boolean appliesTo(@NonNull Environment environment) {
      return environments.isEmpty() || environments.contains(environment);
    }

    /**
     * Whether these environments all lie among others, so these are no wider.
     *
     * @param other environments to compare with
     * @return whether the other applies to every environment, or names every
     *         one of these
     */
    public boolean within(@NonNull Meta other) {
      return other.environments.isEmpty()
          || !environments.isEmpty() && other.environments.containsAll(environments);
    }

    /**
     * Whether these environments and others share an environment.
     *
     * @param other environments to compare with
     * @return whether both apply to some environment
     */
    public boolean overlaps(@NonNull Meta other) {
      return Arrays.stream(Environment.values()).anyMatch(environment -> appliesTo(environment)
          && other.appliesTo(environment));
    }

  }
}
