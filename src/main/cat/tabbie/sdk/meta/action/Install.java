package cat.tabbie.sdk.meta.action;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.content.Artifact;
import cat.tabbie.sdk.revision.Image;
import cat.tabbie.sdk.tree.Namespace;
import lombok.NonNull;

/**
 * Installs an artifact: the method describes the images placing it, for the
 * targets its {@link cat.tabbie.sdk.meta.attribute.Applies} declarations
 * name, and acts where its {@link Closure closure}
 * names, in the Cat's root unless it says otherwise. Every install has the
 * one {@link Signature}: an instance method takes its {@link Context} and
 * installs artifacts of the type declaring it; a static method takes the type
 * it installs, named as its {@link #subject()}, then its context, so another
 * module can contribute an install for it. Installs are named for the
 * closure, and told apart by a qualifier when one type has several:
 * {@code installRoot}, {@code installNamespaces},
 * {@code installListedRoot}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(Install.Signature.class)
public @interface Install {

  /**
   * Returns where the install acts.
   *
   * @return closure, the Cat's root by default
   */
  Closure value() default Closure.ROOT;

  /**
   * Returns the type of artifact a static install installs.
   *
   * @return that type, or empty for the type declaring an instance method
   */
  Class<? extends Artifact>[] subject() default {};

  /**
   * The signature of every install, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Installs an artifact.
     *
     * @param artifact artifact of the install's type
     * @param context  its head, and its place's namespace
     * @return the images placing it
     * @throws IOException when its content cannot be read or is refused
     */
    Set<Image<?>> install(@NonNull Artifact artifact, @NonNull Context context) throws IOException;
  }

  /**
   * What an install is given: its head, the directory accepting the artifact
   * at the one place it is called for, and that place's namespace when there
   * is one. Core calls an install once for each place its closure says: the
   * Cat's root for {@link Closure#ROOT ROOT}, or every namespace for
   * {@link Closure#NAMESPACES NAMESPACES}. Core implements it.
   */
  interface Context extends Action.Context {

    /**
     * Returns the namespace the install's place lies in.
     *
     * @return the namespace, or empty when the place belongs to the Cat as a
     *         whole
     */
    @NonNull
    Optional<Namespace> namespace();
  }
}
