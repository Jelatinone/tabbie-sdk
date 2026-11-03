package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.function.Function;

import cat.tabbie.sdk.minecraft.Version;
import lombok.NonNull;

/**
 * The game versions an action applies to: those from its low bound up to, but
 * not including, its high bound, so two ranges where one's high bound is the
 * other's low bound meet without sharing a version. A bound is a
 * {@link Version.Number release number} written as an identifier writes it,
 * such as {@code 1.20.5} or {@code 26.3}. A bound left out is open, so an
 * action with neither applies to every version.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Versions {

  /**
   * Returns the first version the action applies to.
   *
   * @return release number, or empty for no lower bound
   */
  String low() default "";

  /**
   * Returns the first version the action no longer applies to.
   *
   * @return release number above the low bound, or empty for no upper bound
   */
  String high() default "";

  /**
   * What a {@link Versions} attribute says, read and checked: the versions
   * from its low bound up to, but not including, its high bound.
   *
   * @param low  first version it applies to, or empty for no lower bound
   * @param high first version it no longer applies to, or empty for no upper
   *             bound
   */
  record Meta(@NonNull Optional<Version.Number> low, @NonNull Optional<Version.Number> high) {

    /**
     * Every version, as an action without the attribute applies to.
     */
    public static final Meta EVERY = new Meta(Optional.empty(), Optional.empty());

    /**
     * Checks the low bound is below the high bound.
     *
     * @throws IllegalArgumentException when both are given and the low bound
     *                                  is not below the high bound
     */
    public Meta {
      if (low.isPresent() && high.isPresent() && low.get().compareTo(high.get()) >= 0) {
        throw new IllegalArgumentException(String.format(
            "A version range's low bound, %s, is not below its high bound, %s.", low.get(), high.get()));
      }
    }

    /**
     * Reads what an attribute says.
     *
     * @param versions attribute as written
     * @return its versions
     * @throws IllegalArgumentException when a bound is not exactly a release
     *                                  number, or the low bound is not below
     *                                  the high bound
     */
    public static Meta of(@NonNull Versions versions) {
      Function<String, Optional<Version.Number>> bound = text -> text.isEmpty()
          ? Optional.empty()
          : Optional.of(Version.Number.of(text)
              .filter(number -> number.toString().equals(text))
              .orElseThrow(() -> new IllegalArgumentException(
                  String.format("%s is not a release number such as 1.20.5 or 26.3.", text))));
      return new Meta(bound.apply(versions.low()), bound.apply(versions.high()));
    }
  }
}
