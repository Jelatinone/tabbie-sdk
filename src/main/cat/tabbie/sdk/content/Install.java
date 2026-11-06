package cat.tabbie.sdk.content;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import cat.tabbie.sdk.meta.action.Action;
import cat.tabbie.sdk.meta.action.Closure;
import cat.tabbie.sdk.model.Layout;
import cat.tabbie.sdk.model.Violation;
import cat.tabbie.sdk.model.World;
import cat.tabbie.sdk.plate.Archive;
import cat.tabbie.sdk.plate.Plate;
import cat.tabbie.sdk.revision.Image;
import cat.tabbie.sdk.target.Label;
import cat.tabbie.sdk.target.Platform;
import lombok.NonNull;

/**
 * Installs an artifact: the method describes the images placing it, for the
 * targets its attributes name, and acts where its {@link Closure closure}
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
     * @param context  the work, and where the install places content
     * @return the images placing it
     * @throws IOException when its content cannot be captured or is refused
     */
    Set<Image<?>> install(@NonNull Artifact artifact, @NonNull Context context) throws IOException;
  }

  /**
   * What an install is given: the target, model, plate, archive limits and
   * Worlds of the work, and where it places content, as its closure says: the
   * directory of the Cat's model accepting the artifact for
   * {@link Closure#ROOT ROOT}, or that beneath every World's namespace for
   * {@link Closure#NAMESPACES NAMESPACES}.
   */
  interface Context extends Artifact.Context {

    /**
     * Returns where the install places content: the directories accepting the
     * artifact's type where it acts.
     *
     * @return install of the artifact's type, at every place it acts
     */
    @NonNull
    Installer.Install place();

    /**
     * Makes the context an install acting where its closure says is given.
     *
     * @param artifact artifact being installed
     * @param context  the work: its target, model, plate, archive limits and
     *                 Worlds
     * @param closure  where the install acts
     * @return its context
     * @throws Violation.Unplaced  when the install acts in Worlds and the work
     *                             names none, or a place accepts the
     *                             artifact's type nowhere
     * @throws Violation.Ambiguous when a place accepts it in several
     *                             directories
     */
    static Context of(@NonNull Artifact artifact, @NonNull Artifact.Context context, @NonNull Closure closure) {
      return new Default(context, Installer.Install.of(context, artifact.getClass(), closure));
    }

    /**
     * The context of one install.
     *
     * @param label    selected target
     * @param platform target platform
     * @param model    model the artifact is placed against
     * @param plate    caller-owned plate whose stash keeps what it captures
     * @param archive  limits applied when unpacking archives
     * @param worlds   Worlds the work applies to
     * @param place    where the install places content
     */
    record Default(
        @NonNull Label label,
        @NonNull Platform platform,
        @NonNull Layout.Directory model,
        @NonNull Plate plate,
        @NonNull Archive archive,
        @NonNull Set<World> worlds,
        @NonNull Installer.Install place) implements Context {

      /**
       * Copies the Worlds.
       */
      public Default {
        worlds = Set.copyOf(worlds);
      }

      /**
       * Creates the context of an install of some work.
       *
       * @param context the work: its target, model, plate, archive limits and
       *                Worlds
       * @param place   where the install places content
       */
      public Default(@NonNull Artifact.Context context, @NonNull Installer.Install place) {
        this(context.label(), context.platform(), context.model(), context.plate(), context.archive(),
            context.worlds(), place);
      }
    }
  }
}
