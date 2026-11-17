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
 * name, at the head core gives it: the directory its family's {@link Place}
 * returns, at the Cat's root or in each namespace, as that place's closure
 * says. Every install has the one {@link Signature}: an instance method takes
 * its {@link Context} and installs artifacts of the type declaring it; a
 * static method takes the artifact it installs, then its context, and
 * installs artifacts of its first parameter's type, so another module can
 * contribute an install for a family it does not declare.
 * Installs are told apart by a qualifier when one type has several:
 * {@code install}, {@code installUnpacked}, {@code installArchived}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(Install.Signature.class)
public @interface Install {

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
   * is one. Core calls an install once for each place its family's
   * {@link Place} gives: once at the Cat's root, or in every namespace when
   * the place's closure is {@link Closure#NAMESPACES NAMESPACES}. Core
   * implements it.
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
