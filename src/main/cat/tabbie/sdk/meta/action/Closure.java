package cat.tabbie.sdk.meta.action;

/**
 * Where an action acts: the place the registry has a contribution act in,
 * called once. An action whose contributions may act in different places
 * declares one for each, as its annotation's value, and acts in the root
 * unless it says otherwise; an action that declares none always acts in the
 * root.
 */
public enum Closure {

  /**
   * In the root of the work's model: the Cat itself.
   */
  ROOT,

  /**
   * Beneath the namespace of every World the work applies to, all at once.
   */
  NAMESPACES
}
