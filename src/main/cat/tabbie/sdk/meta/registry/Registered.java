package cat.tabbie.sdk.meta.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Registers a type with tabbie, as what the {@link Registrable registrable
 * kind} it is a type of says: for a kind registered by values, such as
 * distributions, an enum whose every value is registered, or an abstract type,
 * an interface or abstract class, registered as a group of every value of the
 * type; for a kind registered by types, such as content families, the type
 * itself. Registering is how a module, the SDK included, tells tabbie what it
 * adds. While the module compiles, its registered types, and every type
 * declaring a contribution, are listed in one generated {@link Registration};
 * the registry reads what each type is from this annotation, and from its
 * kind, when it loads the module.
 *
 * <p>
 * A group lets an attribute name many values at once, such as
 * {@code @Distributions("java:launcher")} for every Java mod loader. Groups
 * nest as their types do: a value of a type belongs to the type's group, and
 * to the group of every registered type it extends.
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
   * Returns the identifier of the group an abstract type of a kind registered
   * by values is registered as, such as {@code java:launcher}, in the form of
   * a distribution identifier. An enum of values, or a type of a kind
   * registered by types, names none.
   *
   * @return group identifier, or empty for an enum or a type registering
   *         itself
   */
  String value() default "";
}
