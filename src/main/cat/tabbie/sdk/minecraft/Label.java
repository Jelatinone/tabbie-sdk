package cat.tabbie.sdk.minecraft;

import cat.tabbie.sdk.addon.artifact.Artifact;
import lombok.NonNull;

/**
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

  /**
   * Compares target identity independently of release date and classification.
   * 
   * @param other target declaration to compare
   * @return whether edition, game version, distribution, and environment agree
   */
  public boolean match(@NonNull Label other) {
    return version.equals(other.version)
        && distribution.equals(other.distribution)
        && environment == other.environment;
  }

  /**
   * Assesses type support followed by explicit target declarations. Unlisted
   * targets remain unknown; no compatibility between runtime forks is inferred.
   *
   * @param artifact artifact to assess against this target
   * @return structural rejection, declared support, or unknown support
   */
  public Compatibility compatibility(@NonNull Artifact artifact) {
    if (!distribution.supports(artifact.getClass())) {
      return Compatibility.UNSUPPORTED;
    }
    return artifact.labels().stream().anyMatch(label -> label.match(this))
        ? Compatibility.SUPPORTED
        : Compatibility.UNKNOWN;
  }
}
