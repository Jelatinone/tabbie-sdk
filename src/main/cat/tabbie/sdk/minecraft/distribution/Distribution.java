package cat.tabbie.sdk.minecraft.distribution;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import lombok.NonNull;

/**
 * A stateless runtime family describing edition, environments, capabilities,
 * and common content placement. Version resolution, installation conflicts,
 * filesystem observations, and deployment belong to Core.
 *
 * <p>
 * The public values are the enum constants nested in {@link Java} and
 * {@link Bedrock}, such as {@link Java.Launcher.Of#FABRIC}, listed by
 * {@link #values()} and found by {@link #of(String)}; equality is identity.
 */
public sealed interface Distribution permits Java, Bedrock {

  /**
   * Enumerates every canonical distribution value.
   *
   * @return immutable distributions, Java before Bedrock, in declaration order
   */
  static List<Distribution> values() {
    return Stream.<Distribution>concat(
        Java.values().stream(),
        Bedrock.values().stream())
        .toList();
  }

  /**
   * Looks up a canonical distribution by its exact namespaced identifier, for
   * example when reading a persisted lock.
   *
   * @param id case-sensitive identifier such as {@code java:fabric}
   * @return matching distribution, or empty when unknown
   */
  static Optional<Distribution> of(@NonNull String id) {
    return values().stream()
        .filter(distribution -> distribution.id().equals(id))
        .findFirst();
  }

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
    if (!id.matches("(bedrock|java):[a-z0-9]+(-[a-z0-9]+)*")) {
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
