package cat.tabbie.sdk.minecraft;

import cat.tabbie.sdk.platform.layout.Layout;
import lombok.NonNull;

/**
 * A World as an install's model holds it: the name a Cat selects it by, and
 * the namespace holding everything that belongs to it. The distribution
 * decides where a namespace is. Most keep each World in a directory of its
 * own, such as a client's {@code saves/<folder>/} or a Bedrock server's
 * {@code worlds/<level-name>/}; the Bukkit family keeps a World's dimensions
 * in several directories at the Cat's root, so its namespace is the root.
 *
 * <p>
 * A release's layout never names a Cat's Worlds. Core mounts each World into
 * an install's model and names the Worlds the install applies to in its
 * context. An install acting in
 * {@link cat.tabbie.sdk.meta.action.Closure#NAMESPACES namespaces}, such as a
 * datapack's, places its content in each through the directory of its
 * namespace accepting its type, and the context's label says which
 * distribution laid the namespace out. Whether content belongs to a World or
 * to the Cat is the install's to say, and may differ by distribution. How a
 * World's data is stored is not the SDK's concern.
 */
public interface World {

  /**
   * Returns the name a Cat selects this World by, such as a server's
   * {@code level-name}.
   *
   * @return non-blank name
   */
  @NonNull
  String name();

  /**
   * Returns the directory of the model holding everything that belongs to
   * this World, which may be the Cat's root.
   *
   * @return namespace directory
   */
  @NonNull
  Layout.Directory namespace();

  /**
   * Immutable World.
   *
   * @param name      non-blank name
   * @param namespace directory of the model holding the World
   */
  record Default(@NonNull String name, @NonNull Layout.Directory namespace) implements World {

    /**
     * Checks the name.
     *
     * @throws IllegalArgumentException when the name is blank
     */
    public Default {
      if (name.isBlank()) {
        throw new IllegalArgumentException("A World needs a non-blank name.");
      }
    }
  }
}
