package cat.tabbie.sdk.platform.layout;

import java.util.Set;

import lombok.NonNull;

/**
 * What a tree of {@link Node nodes} allows at one of them. Rules compose: a
 * node holds any set of them, and an operation on a node is allowed only when
 * every rule it needs holds there. Only a {@link Node.Declared declared} node
 * holds rules of its own. A node with no permissions of its own, such as a mod
 * present in {@code mods}, follows the permissions of the directory above it
 * when they are checked, but never takes what that directory accepts.
 */
public sealed interface Rule permits Rule.Permission, Rule.Accepts {

  /**
   * An operation a node permits on itself, or on undeclared content beneath a
   * directory.
   */
  enum Permission implements Rule {

    /**
     * Its content may be read.
     */
    READABLE,

    /**
     * An existing file may be changed.
     */
    EDITABLE,

    /**
     * It may be created, or content may be created beneath a directory.
     */
    CREATABLE,

    /**
     * It may be deleted, or managed content beneath a directory may be deleted.
     */
    DELETABLE
  }

  /**
   * The kinds of thing a directory takes as content, by type. Any number of
   * directories may accept the same type, and a type is accepted when it is
   * one of these types or a subtype of one, so accepting a family interface
   * accepts every variant of it.
   *
   * @param types accepted types, at least one
   */
  record Accepts(@NonNull Set<Class<?>> types) implements Rule {

    /**
     * Copies the types and requires at least one.
     *
     * @throws IllegalArgumentException when no type is accepted
     */
    public Accepts {
      types = Set.copyOf(types);
      if (types.isEmpty()) {
        throw new IllegalArgumentException("A directory that accepts content accepts at least one type.");
      }
    }

    /**
     * Accepts the given types.
     *
     * @param types accepted types, at least one
     * @return the rule
     * @throws IllegalArgumentException when no type is given
     */
    public static Accepts of(@NonNull Class<?>... types) {
      return new Accepts(Set.of(types));
    }

    /**
     * Whether a type is one of the accepted types or a subtype of one.
     *
     * @param type type of the content to place
     * @return true when accepted
     */
    public boolean accepts(@NonNull Class<?> type) {
      return types.stream().anyMatch(accepted -> accepted.isAssignableFrom(type));
    }
  }
}
