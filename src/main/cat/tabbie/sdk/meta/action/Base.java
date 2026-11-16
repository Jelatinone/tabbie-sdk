package cat.tabbie.sdk.meta.action;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import cat.tabbie.sdk.tree.Rule;
import lombok.NonNull;

/**
 * Gives a Cat's root its permissions, for the distributions its
 * {@link cat.tabbie.sdk.meta.attribute.Applies} declarations name. Every
 * place beneath the root follows them, and a {@link Place} may tighten them
 * for its own directory; core composes both into the {@link Rule rules} the
 * Cat's nodes report. A base acts on a distribution as a type, not on an
 * instance of it: its {@link Signature} takes only its context, and every
 * base is a static method acting on the distribution type declaring it, or on
 * the type it names as its {@link #subject()}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(Base.Signature.class)
public @interface Base {

  /**
   * Returns the distribution type a base declared outside it applies to.
   *
   * @return that type, or empty for the type declaring the method
   */
  Class<?>[] subject() default {};

  /**
   * The signature of every base, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Gives the root its permissions.
     *
     * @param context the Cat's root
     * @return the permissions the root holds, possibly none
     * @throws IOException when the base depends on a file that cannot be read
     */
    @NonNull
    Set<Rule.Permission> base(@NonNull Context context) throws IOException;
  }

  /**
   * What a base is given: its head, the Cat's root. Core implements it.
   */
  interface Context extends Action.Context {
  }
}
