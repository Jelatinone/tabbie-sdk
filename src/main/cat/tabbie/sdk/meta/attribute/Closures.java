package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where an action acts, for an action that acts in a place, such as
 * installing content: once on the Cat's root, once for each World the work
 * applies to, or once with every World. An action declares whether it acts in
 * a place with {@link cat.tabbie.sdk.meta.action.Action#scoped()}; each of its
 * contributions then says where, and an action that does not act in a place
 * refuses this attribute. Unlike the other attributes, it does not choose
 * which targets a contribution applies to.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Closures {

  /**
   * Returns where the action acts.
   *
   * @return scope
   */
  Closure value();

  /**
   * The places an action can act in.
   */
  enum Closure {

    /**
     * Once, on the Cat's root.
     */
    CAT,

    /**
     * Once for each World the work applies to, given that World.
     */
    WORLD,

    /**
     * Once, given every World the work applies to.
     */
    WORLDS
  }
}
