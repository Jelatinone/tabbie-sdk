package cat.tabbie.sdk.minecraft.distribution;

import java.util.Set;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import lombok.NonNull;

/**
 * A stateless runtime family describing edition, environments, and
 * capabilities. Where content goes is declared by each release's layout, not
 * by the distribution. Version resolution, installation conflicts, filesystem
 * observations, and deployment belong to Core.
 *
 * <p>
 * Distributions are enum constants, such as {@link Java.Launcher.Of#FABRIC},
 * and equality is identity. An enum of them is
 * {@link cat.tabbie.sdk.meta.registry.Registered registered} by the module
 * declaring it, the SDK included, so a module can add distributions of its
 * own; a {@link cat.tabbie.sdk.Registry} lists every registered one, and
 * finds one by identifier.
 */
public interface Distribution {

  /**
   * Checks that an identifier belongs to the Bedrock or Java namespaces, so a
   * mistyped constant fails when its enum loads rather than when a persisted lock
   * is read.
   *
   * @param id candidate identifier
   * @throws IllegalArgumentException when it is not {@code bedrock:} or
   *                                  {@code java:} followed by lowercase
   *                                  letters and digits, optionally in
   *                                  hyphen-separated words
   */
  static void validate(@NonNull String id) {
    if (!Distributions.Meta.IDENTIFIER.matcher(id).matches()) {
      throw new IllegalArgumentException(
          String.format("%s is not a distribution identifier.", id));
    }
  }

  /**
   * Returns a stable, case-sensitive namespaced identifier.
   *
   * @return distribution identifier
   */
  String id();

  /**
   * Returns supported physical runtime environments.
   *
   * @return immutable environments
   */
  Set<Environment> environments();

  /**
   * Returns supported artifact-family class tokens.
   *
   * @return immutable capabilities
   */
  Set<Class<? extends Artifact>> capabilities();

  /**
   * Tests edition membership without discovering a runtime release.
   *
   * @param version discovered game version
   * @return whether the version belongs to the supported edition
   */
  boolean applicable(@NonNull Version version);

  /**
   * Tests a family interface or concrete artifact against family capabilities.
   *
   * @param family family or implementation class
   * @return whether a declared capability is assignable from the supplied type
   */
  default boolean supports(@NonNull Class<? extends Artifact> family) {
    return capabilities().stream().anyMatch(capability -> capability.isAssignableFrom(family));
  }
}
