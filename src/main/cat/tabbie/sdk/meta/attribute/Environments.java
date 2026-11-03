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

import cat.tabbie.sdk.minecraft.Environment;
import lombok.NonNull;

/**
 * The environments an action applies to, such as only dedicated servers.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
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
  record Meta(@NonNull Set<Environment> environments) {

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
  }
}
