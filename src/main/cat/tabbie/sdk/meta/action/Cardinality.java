package cat.tabbie.sdk.meta.action;

/**
 * How many of an action's contributors act for one subject at one target,
 * once precedence has chosen between those that apply.
 */
public enum Cardinality {

  /**
   * At most one: the winner, as an install of one artifact is. None applying
   * means the subject is not supported at the target.
   */
  ONE,

  /**
   * Every winner, one for each value of the action's {@link Action#key() key},
   * as the activations of an artifact are, one for each file they edit.
   * Contributors keyed by different values never meet, so precedence only
   * chooses between those sharing a key.
   */
  EACH,

  /**
   * Exactly one: the winner, which must exist, as a distribution's start
   * must. Core refuses a target none applies to.
   */
  EXACTLY_ONE
}
