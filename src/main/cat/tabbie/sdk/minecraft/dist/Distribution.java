package cat.tabbie.sdk.minecraft.dist;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Behaviourpack;
import cat.tabbie.sdk.addon.artifact.Datapack;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.addon.artifact.Modpack;
import cat.tabbie.sdk.addon.artifact.Plugin;
import cat.tabbie.sdk.addon.artifact.Resourcepack;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Version;
import lombok.NonNull;

/**
 * A stateless runtime family describing edition, environments, capabilities,
 * and common content placement. Version resolution, installation conflicts,
 * filesystem observations, and deployment belong to Core.
 *
 * Canonical enum aliases delegate behavior to records but are not equal to
 * them. Separately constructed instances of the same stateless record are
 * equal.
 *
 */
public sealed interface Distribution permits Java, Bedrock {

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
   * Determines image layout for this distribution; context implementations may
   * supply additional custom data. Including server resource-pack delivery. This
   * method does not discover installation directories.
   *
   * @param artifact reference artifact
   * @param context  reference context
   *
   * @return distribution relative context
   *
   * @throws IOException              when no matching layout can be determined
   * @throws IllegalArgumentException when this distribution does not support the
   *                                  artifact type
   */
  @SuppressWarnings("unused")
  default Artifact.Layout layout(@NonNull Artifact artifact, @NonNull Artifact.Context context) throws IOException {

    Environment environment = context.label().environment();
    if (!environments().contains(environment)) {
      throw new IllegalArgumentException(String.format(
          "Distribution does not support %s environment",
          environment.getClass().getSimpleName()));
    }
    if (!supports(artifact.getClass())) {
      throw new IllegalArgumentException(String.format(
          "Distribution does not support %s artifact",
          artifact.getClass().getSimpleName()));
    }

    return switch (artifact) {
      case Mod mod ->
        new Artifact.Layout.Root(
            false,
            Path.of("mods"));
      case Plugin plugin ->
        new Artifact.Layout.Root(
            false,
            Path.of("plugins"));
      case Resourcepack resourcepack ->
        new Artifact.Layout.Root(
            false,
            Path.of("resourcepacks"));
      case Datapack datapack ->
        new Artifact.Layout.World(
            true,
            Path.of("datapacks", artifact.artifactId().id().toString()));
      case Behaviourpack behaviourpack ->
        new Artifact.Layout.World(
            true,
            Path.of("behavior_packs", artifact.artifactId().id().toString()));
      case Modpack modpack ->
        new Artifact.Layout.Root(
            true,
            Path.of("mods"));

      default -> {
        throw new IOException(String.format(
            "Distribution has no default layout for %s artifact",
            Artifact.class.getSimpleName()));
      }
    };
  }

  /**
   * A published selection of artifacts. Every artifact must be a file of this
   * build and explicitly support every build label, while artifacts may declare
   * additional supported targets. External dependency availability is resolved
   * later.
   *
   * @param coordinates   exact release coordinates
   * @param releaseName   non-blank display name
   * @param releaseDate   publication instant
   * @param releaseNumber positive provider-assigned release number
   * @param labels        nonempty targets advertised for the entire build
   * @param content       nonempty immutable artifact selection with distinct
   *                      file coordinates
   */
  record Build(
      @NonNull Coordinate.Build coordinates,
      @NonNull String releaseName,
      @NonNull Instant releaseDate,
      long releaseNumber,
      @NonNull Set<Label> labels,
      @NonNull Set<Instance> content) implements Release<Instance> {

    /**
     * Copies and checks the published artifact selection and advertised labels.
     *
     * @throws IllegalArgumentException when publication invariants are violated
     */
    public Build {
      labels = Set.copyOf(labels);
      content = Release.validate(coordinates, releaseName, releaseNumber, content);
      validate(labels);
    }

    /**
     * Checks copied constructor inputs before record fields are initialized.
     * Payload ownership and distinct file coordinates are checked by
     * {@link Release#validate(Coordinate.Build, String, long, Set)}.
     *
     * @param labels    advertised targets
     * @param artifacts directly bundled artifacts
     */
    private static void validate(@NonNull Set<Label> labels) {
      if (labels.isEmpty()) {
        throw new IllegalArgumentException("A build needs supported labels.");
      }
    }
  }
}
