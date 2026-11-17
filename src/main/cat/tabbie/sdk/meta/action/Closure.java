package cat.tabbie.sdk.meta.action;

import java.util.Optional;

import cat.tabbie.sdk.tree.Namespace;

/**
 * Where an action acts: the place the registry has a contribution act in,
 * called once. An action whose contributions may act in different places,
 * such as {@link Place}, declares one for each, as its annotation's value,
 * and acts in the root unless it says otherwise; an action that declares none
 * always acts in the root. The
 * {@link cat.tabbie.sdk.meta.registry.Registry#check(cat.tabbie.sdk.catalog.Product.Build)
 * registry checks} every release places what each closure acts beneath.
 */
public enum Closure {

  /**
   * In the root of the work's model: the Cat itself.
   */
  ROOT {
    @Override
    public Optional<Class<?>> beneath() {
      return Optional.empty();
    }
  },

  /**
   * Beneath the head of every namespace the work applies to, all at once.
   */
  NAMESPACES {
    @Override
    public Optional<Class<?>> beneath() {
      return Optional.of(Namespace.class);
    }
  };

  /**
   * Returns the type whose places a contribution acting here acts beneath,
   * which a release must therefore place: nothing at the root, which is
   * always there, and {@link Namespace namespaces} in namespaces.
   *
   * @return the type acted beneath, or empty at the root
   */
  public abstract Optional<Class<?>> beneath();
}
