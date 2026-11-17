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
 * Bedrock runtime families, registered as the group {@code bedrock:base} of
 * every Bedrock distribution. Game versions and concrete runtime releases are
 * described and resolved separately from these stateless family values. It
 * gives every Bedrock Cat's root its permissions, and places what every
 * Bedrock distribution keeps in the same place: its Worlds and its own files.
 */
@Registered("bedrock:base")
public interface Bedrock extends Distribution {

  /**
   * Supports clients and dedicated servers unless a family narrows it.
   *
   * @return immutable environments
   */
  @Override
  default Set<Environment> environments() {
    return Set.of(Environment.CLIENT, Environment.SERVER);
  }

  /**
   * Accepts Bedrock Edition game versions unless a family narrows them.
   *
   * @param version discovered game version
   * @return whether the version belongs to Bedrock Edition
   */
  @Override
  default boolean applicable(@NonNull Version version) {
    return Version.Bedrock.applicable(version);
  }

  /**
   * Gives a Bedrock Cat's root every permission, on every Bedrock
   * distribution; places tighten from there.
   *
   * @param context the Cat's root
   * @return every permission
   */
  @Base
  @Applies(to = "bedrock:base")
  static Set<Rule.Permission> base(@NonNull Base.Context context) {
    return Set.of(Rule.Permission.values());
  }

  /**
   * Places namespaces in {@code minecraftWorlds/}, where a Bedrock client
   * keeps each World in a directory of its own.
   *
   * @param context the Cat's root
   * @return the directory accepting namespaces
   */
  @Place
  @Subject(Namespace.class)
  @Applies(to = "bedrock:base", environments = Environment.CLIENT)
  static Node.Directory placeMinecraftWorldsRoot(@NonNull Place.Context context) {
    return context.head().directory("minecraftWorlds");
  }

  /**
   * Places namespaces in {@code worlds/}, where a Bedrock server keeps each
   * World in a directory named by its {@code level-name}.
   *
   * @param context the Cat's root
   * @return the directory accepting namespaces
   */
  @Place
  @Subject(Namespace.class)
  @Applies(to = "bedrock:base", environments = Environment.SERVER)
  static Node.Directory placeWorldsRoot(@NonNull Place.Context context) {
    return context.head().directory("worlds");
  }

  /**
   * Places a release's distributables at the root, beside the files they run
   * with.
   *
   * @param context the Cat's root
   * @return the root, accepting distributables
   */
  @Place
  @Subject(Distributable.class)
  @Applies(to = "bedrock:base")
  static Node.Directory placeReleaseRoot(@NonNull Place.Context context) {
    return context.head();
  }
}
