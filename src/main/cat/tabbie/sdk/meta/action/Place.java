package cat.tabbie.sdk.meta.action;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import cat.tabbie.sdk.tree.Namespace;
import cat.tabbie.sdk.tree.Node;
import cat.tabbie.sdk.tree.Rule;
import lombok.NonNull;

/**
 * Places what a Cat accepts: the method returns the directory accepting its
 * subject, found from the head its {@link Context} gives, for the targets its
 * {@link cat.tabbie.sdk.meta.attribute.Applies} declarations name, at the
 * Cat's root or in every namespace, as its {@link Closure closure} says. A
 * content family, the distributables of a release, and
 * {@link Namespace namespaces} themselves are placed this way. Core composes
 * every applying place, with the {@link Base base}, into the
 * {@link Rule rules} the Cat's nodes report: the directory accepts the
 * subject and holds the permissions the place writes, and what it accepts is
 * installed there.
 *
 * <p>
 * Core places a Cat before any content exists, so a place acts on a type, not
 * on an instance of it: its {@link Signature} takes only its context, and
 * every place is a static method acting on the type declaring it, or on the
 * type it names as its {@link #subject()}. Places are named for the closure,
 * and told apart by a qualifier when one type has several:
 * {@code placeRoot}, {@code placeNamespaces}, {@code placeSavesRoot}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(Place.Signature.class)
public @interface Place {

  /**
   * Returns where the place lies.
   *
   * @return closure, the Cat's root by default
   */
  Closure value() default Closure.ROOT;

  /**
   * Returns the type a place declared outside it places.
   *
   * @return that type, or empty for the type declaring the method
   */
  Class<?>[] subject() default {};

  /**
   * Returns the permissions the placed directory holds, tightening those of
   * the place above it.
   *
   * @return permissions, or empty to follow the place above
   */
  Rule.Permission[] permissions() default {};

  /**
   * The signature of every place, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Places a subject.
     *
     * @param context the Cat's root, or the head of one namespace, and that
     *                namespace
     * @return the directory accepting the subject, beneath the context's head
     *         or the head itself
     * @throws IOException when the place depends on a file that cannot be read
     */
    @NonNull
    Node.Directory place(@NonNull Context context) throws IOException;
  }

  /**
   * What a place is given: its head, the Cat's root or the head of the one
   * namespace it is called for, and that namespace when there is one. Core
   * calls a place once at the root, or once for every namespace when its
   * closure is {@link Closure#NAMESPACES NAMESPACES}. Core implements it.
   */
  interface Context extends Action.Context {

    /**
     * Returns the namespace the place lies in.
     *
     * @return the namespace, or empty at the Cat's root
     */
    @NonNull
    Optional<Namespace> namespace();
  }
}
