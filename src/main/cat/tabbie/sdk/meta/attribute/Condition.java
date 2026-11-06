package cat.tabbie.sdk.meta.attribute;

import lombok.NonNull;

/**
 * What {@link Applies} declarations say about the targets a contribution
 * applies to, read and checked: one {@link Region}, or a contribution's
 * {@link Conditions}, all of its regions together. A condition is plain data:
 * the processor reads it while a module compiles, and the registry reads the
 * same record from the same annotations when it loads the module.
 */
public sealed interface Condition permits Region, Conditions {

  /**
   * Whether this applies to a target.
   *
   * @param target the target a contribution may be chosen for
   * @return whether the target is among those this applies to
   */
  boolean appliesTo(@NonNull Conditions.Target target);
}
