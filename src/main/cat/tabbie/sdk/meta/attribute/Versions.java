package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

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
@Retention(RetentionPolicy.RUNTIME)
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
  record Meta(
      @NonNull Optional<Version.Number> low,
      @NonNull Optional<Version.Number> high) implements Condition {

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

    /**
     * Whether a target's version is among these.
     *
     * @param target the target a contribution may be chosen for
     * @return whether its version's release number lies within both bounds
     */
    @Override
    public boolean appliesTo(@NonNull Conditions.Target target) {
      return appliesTo(target.version());
    }

    /**
     * Whether a version is among these. A version without a release number,
     * such as a week snapshot, lies within no bound, so it is among these only
     * when they are every version.
     *
     * @param version game version of a target
     * @return whether its release number lies within both bounds
     */
    public boolean appliesTo(@NonNull Version version) {
      if (low.isEmpty() && high.isEmpty()) {
        return true;
      }
      return version.number()
          .filter(number -> low.map(bound -> bound.compareTo(number) <= 0).orElse(true))
          .filter(number -> high.map(bound -> number.compareTo(bound) < 0).orElse(true))
          .isPresent();
    }

    /**
     * Whether these versions all lie within others, so these are no wider.
     *
     * @param other versions to compare with
     * @return whether neither bound reaches past the other's
     */
    public boolean within(@NonNull Meta other) {
      return other.low.map(bound -> low.filter(own -> own.compareTo(bound) >= 0).isPresent()).orElse(true)
          && other.high.map(bound -> high.filter(own -> own.compareTo(bound) <= 0).isPresent()).orElse(true);
    }

    /**
     * Whether these versions and others share a version: whether the later of
     * the two low bounds lies below the earlier of the two high bounds.
     *
     * @param other versions to compare with
     * @return whether some version lies within both
     */
    public boolean overlaps(@NonNull Meta other) {
      Optional<Version.Number> lowest = Stream.of(low, other.low)
          .flatMap(Optional::stream)
          .max(Comparator.naturalOrder());
      Optional<Version.Number> highest = Stream.of(high, other.high)
          .flatMap(Optional::stream)
          .min(Comparator.naturalOrder());
      return lowest.isEmpty() || highest.isEmpty() || lowest.get().compareTo(highest.get()) < 0;
    }

  }
}
