package cat.tabbie.sdk.minecraft.distribution;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.meta.attribute.Conditions;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Version;
import cat.tabbie.sdk.platform.Platform;
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

  /**
   * Every distribution, and group of them, the loaded modules register: the
   * vocabulary targets are named in. Lookups by identifier go through it
   * rather than a global list, so code sees exactly what core has loaded.
   *
   * @param distributions every registered distribution
   * @param groups        every registered group of distributions, by its
   *                      identifier
   */
  record Registry(
      @NonNull List<Distribution> distributions,
      @NonNull Map<String, Class<? extends Distribution>> groups) {

    /**
     * Copies the distributions and groups, and checks that no two share an
     * identifier.
     *
     * @throws IllegalArgumentException when two distributions or groups share
     *                                  an identifier
     */
    public Registry {
      distributions = List.copyOf(distributions);
      groups = Map.copyOf(groups);
      Set<String> ids = new HashSet<>();
      Stream.concat(distributions.stream().map(Distribution::id), groups.keySet().stream())
          .filter(id -> !ids.add(id))
          .findFirst()
          .ifPresent(id -> {
            throw new IllegalArgumentException(String.format("Two distributions or groups are registered as %s.",
                id));
          });
    }

    /**
     * Finds a registered distribution by identifier, for example when reading
     * a persisted lock.
     *
     * @param id case-sensitive identifier such as {@code java:fabric}
     * @return the distribution, or empty when none is registered so
     */
    public Optional<Distribution> distribution(@NonNull String id) {
      return distributions.stream()
          .filter(distribution -> distribution.id().equals(id))
          .findFirst();
    }

    /**
     * Names a distribution as attributes name it: by its own identifier, and
     * the identifier of every group it belongs to.
     *
     * @param distribution a distribution
     * @return its names
     */
    public Set<String> names(@NonNull Distribution distribution) {
      return Stream.concat(Stream.of(distribution.id()), groups.entrySet().stream()
          .filter(group -> group.getValue().isInstance(distribution))
          .map(Map.Entry::getKey))
          .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Names every registered distribution as attributes name it, as
     * contributions compare the distributions they apply to.
     *
     * @return each distribution's names
     */
    public Set<Set<String>> names() {
      return distributions.stream()
          .map(this::names)
          .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Describes a selected target on a platform as contributions are chosen
     * for it.
     *
     * @param label    the selected target
     * @param platform the platform it runs on
     * @return the target, its distribution named by its groups too
     */
    public Conditions.Target target(@NonNull Label label, @NonNull Platform platform) {
      return new Conditions.Target(names(label.distribution()), label.environment(), platform, label.version());
    }
  }
}
