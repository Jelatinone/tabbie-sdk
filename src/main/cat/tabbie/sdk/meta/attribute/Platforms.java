package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.platform.Platform;
import lombok.NonNull;

/**
 * A platform an action applies to: one operating system kernel together with
 * one processor architecture, such as a native server binary built for Linux
 * on x64. It is repeated once for each platform the action applies to, and
 * leaving it out applies to every platform.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(Platforms.List.class)
public @interface Platforms {

  /**
   * Returns the platform's operating system kernel.
   *
   * @return kernel
   */
  Platform.Kernel kernel();

  /**
   * Returns the platform's processor architecture.
   *
   * @return architecture
   */
  Platform.Architecture architecture();

  /**
   * Holds an action's platforms when it names several; written by the
   * compiler, not by hand.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @interface List {

    /**
     * Returns the platforms the action applies to.
     *
     * @return platforms, in the order they are written
     */
    Platforms[] value();
  }

  /**
   * What an action's {@link Platforms} attributes say together, read and
   * checked.
   *
   * @param platforms platforms, empty for every platform
   */
  record Meta(@NonNull Set<Platform> platforms) implements Condition {

    /**
     * Every platform, as an action without the attribute applies to.
     */
    public static final Meta EVERY = new Meta(Set.of());

    /**
     * Copies the platforms.
     */
    public Meta {
      platforms = Set.copyOf(platforms);
    }

    /**
     * Reads what an action's attributes say together.
     *
     * @param platforms attributes as written, in order
     * @return their platforms, every platform when there are none
     */
    public static Meta of(@NonNull Platforms... platforms) {
      return new Meta(Arrays.stream(platforms)
          .map(platform -> new Platform(platform.kernel(), platform.architecture()))
          .collect(Collectors.toSet()));
    }

    /**
     * Whether a platform is among these.
     *
     * @param platform platform, or empty for one no contribution names
     * @return whether this applies to every platform, or names it
     */
    public boolean includes(@NonNull Optional<Platform> platform) {
      return platforms.isEmpty() || platform.filter(platforms::contains).isPresent();
    }

    /**
     * Whether a target's platform is among these.
     *
     * @param target the target a contribution may be chosen for
     * @return whether this applies to every platform, or names the target's
     */
    @Override
    public boolean appliesTo(@NonNull Conditions.Target target) {
      return appliesTo(target.platform());
    }

    /**
     * Whether a platform is among these.
     *
     * @param platform a platform
     * @return whether this applies to every platform, or names it
     */
    public boolean appliesTo(@NonNull Platform platform) {
      return platforms.isEmpty() || platforms.contains(platform);
    }

    /**
     * Whether these platforms all lie among others, so these are no wider.
     *
     * @param other platforms to compare with
     * @return whether the other applies to every platform, or names every one
     *         of these
     */
    public boolean within(@NonNull Meta other) {
      return other.platforms.isEmpty() || !platforms.isEmpty() && other.platforms.containsAll(platforms);
    }

    /**
     * Whether these platforms and others share a platform.
     *
     * @param other platforms to compare with
     * @return whether either applies to every platform, or both name one
     */
    public boolean overlaps(@NonNull Meta other) {
      return platforms.isEmpty() || other.platforms.isEmpty() || platforms.stream().anyMatch(other.platforms::contains);
    }

  }
}
