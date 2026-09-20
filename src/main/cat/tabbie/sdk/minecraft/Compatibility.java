package cat.tabbie.sdk.minecraft;

/**
 * Represents a ternary state of compatibility for a minecraft-related
 * operation.
 */
public enum Compatibility {

  /**
   * The available declaration explicitly supports the target.
   */
  SUPPORTED,

  /**
   * The target is not covered by the available declaration.
   */
  UNKNOWN,

  /**
   * A known structural constraint prevents use.
   */
  UNSUPPORTED
}
