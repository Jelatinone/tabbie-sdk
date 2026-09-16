package cat.tabbie.sdk.minecraft;

import lombok.NonNull;

/**
 *
 * <h1>Label</h1>
 *
 * One structurally valid target declaration. This checks edition and runtime
 * environment, not whether an adapter has discovered this exact game release.
 *
 * @param version      discovered game version
 * @param distribution runtime distribution
 * @param environment  physical runtime environment
 */
public record Label(@NonNull Version version, @NonNull Distribution distribution, @NonNull Environment environment) {

  public Label {
    if (!distribution.applicable(version)) {
      throw new IllegalArgumentException("Distribution does not support this version!");
    }
    if (!distribution.environments().contains(environment)) {
      throw new IllegalArgumentException("Distribution does not support this environment");
    }
  }
}
