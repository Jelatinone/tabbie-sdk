package cat.tabbie.sdk.distribution;

import java.util.Set;

import cat.tabbie.sdk.meta.action.Base;
import cat.tabbie.sdk.meta.action.Place;
import cat.tabbie.sdk.meta.attribute.Applies;
import cat.tabbie.sdk.meta.attribute.Subject;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Version;
import cat.tabbie.sdk.tree.Namespace;
import cat.tabbie.sdk.tree.Node;
import cat.tabbie.sdk.tree.Rule;
import lombok.NonNull;

/**
 * Java runtime families, registered as the group {@code java:base} of every
 * Java distribution. Game versions and concrete runtime releases are
 * described and resolved separately from these stateless family values. It
 * gives every Java Cat's root its permissions, and places what every Java
 * distribution keeps in the same place: its Worlds and its own files.
 */
@Registered("java:base")
public interface Java extends Distribution {

  @Override
  default Set<Environment> environments() {
    return Set.of(Environment.CLIENT, Environment.SERVER);
  }

  @Override
  default boolean applicable(@NonNull Version version) {
    return Version.Java.applicable(version);
  }

  /**
   * Gives a Java Cat's root every permission, on every Java
   * distribution; places tighten from there.
   *
   * @param context the Cat's root
   * @return every permission
   */
  @Base
  @Applies(to = "java:base")
  static Set<Rule.Permission> base(@NonNull Base.Context context) {
    return Set.of(Rule.Permission.values());
  }

  /**
   * Places namespaces in {@code saves/}, where a Java client keeps each World
   * in a directory of its own.
   *
   * @param context the Cat's root
   * @return the directory accepting namespaces
   */
  @Place
  @Subject(Namespace.class)
  @Applies(to = "java:base", environments = Environment.CLIENT)
  static Node.Directory placeSavesRoot(@NonNull Place.Context context) {
    return context.head().directory("saves");
  }

  /**
   * Places namespaces at the root, where a Java server keeps each World in a
   * directory named by its {@code level-name}.
   *
   * @param context the Cat's root
   * @return the root, accepting namespaces
   */
  @Place
  @Subject(Namespace.class)
  @Applies(to = "java:base", environments = Environment.SERVER)
  static Node.Directory placeLevelsRoot(@NonNull Place.Context context) {
    return context.head();
  }

  /**
   * Places a client release's distributables in {@code versions/}, where a
   * launcher reads them.
   *
   * @param context the Cat's root
   * @return the directory accepting distributables
   */
  @Place
  @Subject(Distributable.class)
  @Applies(to = "java:base", environments = Environment.CLIENT)
  static Node.Directory placeVersionsRoot(@NonNull Place.Context context) {
    return context.head().directory("versions");
  }

  /**
   * Places a server release's distributables at the root, beside the files
   * they run with.
   *
   * @param context the Cat's root
   * @return the root, accepting distributables
   */
  @Place
  @Subject(Distributable.class)
  @Applies(to = "java:base", environments = Environment.SERVER)
  static Node.Directory placeServerRoot(@NonNull Place.Context context) {
    return context.head();
  }
}
