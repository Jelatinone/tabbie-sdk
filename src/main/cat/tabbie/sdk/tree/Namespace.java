package cat.tabbie.sdk.tree;

import lombok.NonNull;

/**
 * A named part of a Cat that content can belong to, rather than to the Cat
 * as a whole: the name a Cat selects it by, and its head, the directory
 * holding everything that belongs to it. A World is one. Its head is the
 * directory named for it beneath the directory accepting namespaces, which a
 * {@link cat.tabbie.sdk.meta.action.Place place} for namespaces gives: a Java
 * client's {@code saves/<folder>/}, a Java server's {@code <level-name>/} at
 * its root, or a Bedrock server's {@code worlds/<level-name>/}. The Bukkit
 * family keeps a World's other dimensions in directories beside it, which
 * hold World data rather than content.
 *
 * <p>
 * Core names the namespace an install's place lies in through the install's
 * {@link cat.tabbie.sdk.meta.action.Install.Context#namespace() context}.
 * Whether content belongs to a namespace or to the Cat is its place's
 * {@link cat.tabbie.sdk.meta.action.Closure closure} to say, and may differ
 * by distribution. How a namespace's data is stored is not the SDK's concern.
 */
public interface Namespace {

  /**
   * Returns the name a Cat selects this namespace by, such as a server's
   * {@code level-name}.
   *
   * @return non-blank name
   */
  @NonNull
  String name();

  /**
   * Returns the directory holding everything that belongs to this namespace,
   * which may be the Cat's root.
   *
   * @return the namespace's head
   */
  @NonNull
  Node.Directory head();

  /**
   * Immutable namespace.
   *
   * @param name non-blank name
   * @param head directory holding the namespace
   */
  record Default(@NonNull String name, @NonNull Node.Directory head) implements Namespace {

    /**
     * Checks the name.
     *
     * @throws IllegalArgumentException when the name is blank
     */
    public Default {
      if (name.isBlank()) {
        throw new IllegalArgumentException("A namespace needs a non-blank name.");
      }
    }
  }
}
