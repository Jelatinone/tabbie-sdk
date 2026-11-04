package cat.tabbie.sdk.meta.attribute;

import lombok.NonNull;

/**
 * What an attribute says about the targets a contribution applies to, read
 * and checked. Each attribute choosing targets nests one as its {@code Meta}
 * record, and {@link Conditions} holds all of them together. A condition is
 * plain data: the processor reads it while a module compiles, and the
 * registry reads the same record from the same annotation when it loads the
 * module.
 */
public sealed interface Condition
    permits Versions.Meta, Distributions.Meta, Environments.Meta, Platforms.Meta, Conditions {

  /**
   * Whether this applies to a target.
   *
   * @param target the target a contribution may be chosen for
   * @return whether the target is among those this applies to
   */
  boolean appliesTo(@NonNull Conditions.Target target);
}
