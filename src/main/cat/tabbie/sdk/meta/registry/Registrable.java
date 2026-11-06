package cat.tabbie.sdk.meta.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a registrable kind: a base contract whose subtypes a module registers
 * with {@link Registered}, such as distributions or content families. The
 * processors and the registry learn what a registered type is from the kind it
 * is a type of, so neither names any kind, and a new kind is only a contract
 * carrying this annotation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Registrable {

  /**
   * Returns how the kind's types register.
   *
   * @return how they register
   */
  Kind value();

  /**
   * How the types of a registrable kind register.
   */
  enum Kind {

    /**
     * As values: each constant of a registered enum of the kind is registered,
     * and a registered abstract type of it is a group of every value of the
     * type, named by its identifier, as distributions are.
     */
    VALUES,

    /**
     * As types: each registered type of the kind is registered itself, as
     * content families are.
     */
    TYPES
  }
}
