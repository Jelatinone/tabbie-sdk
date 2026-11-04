package cat.tabbie.sdk.meta.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.minecraft.distribution.Distribution;

/**
 * Registers a type with tabbie: an enum of {@link Distribution distributions},
 * whose every value is registered; an abstract type of distributions, an
 * interface or abstract class, registered as a group of every distribution
 * of the type; or a content family, a type of {@link Artifact}. Registering
 * is how a module, the SDK included, tells tabbie what it adds. While the
 * module compiles, its registered types, and every type declaring a
 * contribution, are listed in one generated {@link Registration}; the
 * registry reads what each type is from this annotation when it loads the
 * module.
 *
 * <p>
 * A group lets an attribute name many distributions at once, such as
 * {@code @Distributions("java:launcher")} for every Java mod loader. Groups
 * nest as their types do: a distribution of a type belongs to the type's
 * group, and to the group of every registered type it extends.
 *
 * <p>
 * A registered type is public, as is every type enclosing it, since the
 * generated registration names it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Registered {

  /**
   * Returns the identifier of the group an abstract type of distributions is
   * registered as, such as {@code java:launcher}, in the form of a
   * distribution identifier. An enum of distributions or a content family
   * names none.
   *
   * @return group identifier, or empty for an enum or a family
   */
  String value() default "";
}
