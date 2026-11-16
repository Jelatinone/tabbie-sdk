package cat.tabbie.sdk.tree;

import java.util.Set;

import lombok.NonNull;

/**
 * What a place allows and accepts. A {@link Node} reports the rules declared
 * at its place, which core composes from the
 * {@link cat.tabbie.sdk.meta.action.Base @Base} and
 * {@link cat.tabbie.sdk.meta.action.Place @Place} contributions applying to a
 * Cat: the root holds the permissions its base gives, and each placed
 * directory accepts its subject and holds the permissions its place writes.
 * Rules compose: a place holds any set of them, and an operation is allowed
 * only when every permission it needs holds there. A place declaring no
 * permissions, or not declared at all, such as a mod in {@code mods}, follows
 * the permissions of the place above it, but never takes what that place
 * accepts.
 */
public sealed interface Rule permits Rule.Permission, Rule.Accepts {

  /**
   * An operation permitted at a place, and beneath it wherever nothing
   * declares other permissions.
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
