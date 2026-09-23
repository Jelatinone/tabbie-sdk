package cat.tabbie.sdk.minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Behaviourpack;
import cat.tabbie.sdk.addon.artifact.Datapack;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.addon.artifact.Modpack;
import cat.tabbie.sdk.addon.artifact.Plugin;
import cat.tabbie.sdk.addon.artifact.Resourcepack;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

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
public sealed interface Distribution permits Distribution.Java, Distribution.Bedrock {

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
        new Artifact.Layout.Root(false, Path.of("mods"));

      case Plugin plugin when this instanceof Java.Manager ->
        new Artifact.Layout.Root(false, Path.of("plugins"));

      case Resourcepack resourcepack ->
        new Artifact.Layout.Root(false, Path.of("resourcepacks"));

      case Datapack datapack ->
        new Artifact.Layout.World(true, Path.of("datapacks", artifact.artifactId().id().toString()));

      case Behaviourpack behaviourpack ->
        new Artifact.Layout.World(true, Path.of("behavior_packs", artifact.artifactId().id().toString()));

      case Modpack modpack when this instanceof Java.Launcher ->
        new Artifact.Layout.Root(true, Path.of("mods"));

      default -> {
        throw new IOException(String.format(
            "Distribution has no default layout for %s artifact",
            Artifact.class.getSimpleName()));
      }
    };
  }

  /**
   * Java runtime families. Game versions and concrete runtime releases are
   * described and resolved separately from these stateless family values.
   */
  sealed interface Java extends Distribution {

    /**
     * Canonical lookup aliases. Each delegates behavior to its concrete record,
     * but remains a distinct value for equality and label matching.
     */
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true)
    enum Of implements Java {

      /**
       * Canonical alias of {@link Java.Native}.
       */
      JAVA_NATIVE(new Java.Native()),

      /**
       * Canonical alias of {@link Java.Fabric}.
       */
      FABRIC(new Java.Fabric()),
      /**
       * Canonical alias of {@link Java.Quilt}.
       */
      QUILT(new Java.Quilt()),
      /**
       * Canonical alias of {@link Java.Forge}.
       */
      FORGE(new Java.Forge()),
      /**
       * Canonical alias of {@link Java.NeoForge}.
       */
      NEO_FORGE(new Java.NeoForge()),

      /**
       * Canonical alias of {@link Java.CraftBukkit}.
       */
      CRAFT_BUKKIT(new Java.CraftBukkit()),
      /**
       * Canonical alias of {@link Java.Spigot}.
       */
      SPIGOT(new Java.Spigot()),
      /**
       * Canonical alias of {@link Java.Paper}.
       */
      PAPER(new Java.Paper()),
      /**
       * Canonical alias of {@link Java.Purpur}.
       */
      PURPUR(new Java.Purpur()),
      /**
       * Canonical alias of {@link Java.Folia}.
       */
      FOLIA(new Java.Folia());

      @NonNull
      Distribution distribution;

      @Override
      public String id() {
        return distribution.id();
      }

      @Override
      public Set<Environment> environments() {
        return distribution.environments();
      }

      @Override
      public Set<Class<? extends Artifact>> capabilities() {
        return distribution.capabilities();
      }

      @Override
      public boolean applicable(@NonNull Version version) {
        return distribution.applicable(version);
      }

      @Override
      public Artifact.Layout layout(@NonNull Artifact artifact, @NonNull Artifact.Context context) throws IOException {
        return distribution.layout(artifact, context);
      }
    }

    @Override
    default Set<Environment> environments() {
      return Set.of(Environment.CLIENT, Environment.SERVER);
    }

    @Override
    default Set<Class<? extends Artifact>> capabilities() {
      return Set.of(Datapack.class, Resourcepack.Java.class, Modpack.class);
    }

    @Override
    default boolean applicable(@NonNull Version version) {
      return Version.Java.applicable(version);
    }

    /**
     * Native runtime family; exact releases are provider-resolved.
     */
    record Native() implements Java {

      @Override
      public String id() {
        return "java:native";
      }
    }

    /**
     * Fabric runtime family; exact releases are provider-resolved.
     */
    record Fabric() implements Launcher {

      @Override
      public String id() {
        return "java:fabric";
      }
    }

    /**
     * Quilt runtime family; exact releases are provider-resolved.
     */
    record Quilt() implements Launcher {

      @Override
      public String id() {
        return "java:quilt";
      }
    }

    /**
     * Forge runtime family; exact releases are provider-resolved.
     */
    record Forge() implements Launcher {
      @Override
      public String id() {
        return "java:forge";
      }
    }

    /**
     * NeoForge runtime family; exact releases are provider-resolved.
     */
    record NeoForge() implements Launcher {
      @Override
      public String id() {
        return "java:neoforge";
      }
    }

    /**
     * CraftBukkit runtime family; exact releases are provider-resolved.
     */
    record CraftBukkit() implements Manager {
      @Override
      public String id() {
        return "java:craftbukkit";
      }
    }

    /**
     * Spigot runtime family; exact releases are provider-resolved.
     */
    record Spigot() implements Manager {

      @Override
      public String id() {
        return "java:spigot";
      }
    }

    /**
     * Paper runtime family; exact releases are provider-resolved.
     */
    record Paper() implements Manager {

      @Override
      public String id() {
        return "java:paper";
      }
    }

    /**
     * Purpur runtime family; exact releases are provider-resolved.
     */
    record Purpur() implements Manager {

      @Override
      public String id() {
        return "java:purpur";
      }
    }

    /**
     * Folia runtime family; exact releases are provider-resolved.
     */
    record Folia() implements Manager {

      @Override
      public String id() {
        return "java:folia";
      }
    }

    /**
     * Java loaders supporting mods on clients and dedicated servers.
     */
    sealed interface Launcher extends Java {

      @Override
      default Set<Class<? extends Artifact>> capabilities() {
        return Set.of(Mod.class, Datapack.class, Resourcepack.Java.class, Modpack.class);
      }
    }

    /**
     * Java plugin manager servers.
     */
    sealed interface Manager extends Java {

      @Override
      default Set<Environment> environments() {
        return Set.of(Environment.SERVER);
      }

      @Override
      default Set<Class<? extends Artifact>> capabilities() {
        return Set.of(Plugin.class, Datapack.class, Resourcepack.Java.class,
            Modpack.class);
      }
    }
  }

  /**
   * Bedrock runtime families. Game versions and concrete runtime releases are
   * described and resolved separately from these stateless family values.
   */
  sealed interface Bedrock extends Distribution {

    @Override
    default Set<Environment> environments() {
      return Set.of(Environment.CLIENT, Environment.SERVER);
    }

    @Override
    default Set<Class<? extends Artifact>> capabilities() {
      return Set.of(Behaviourpack.class, Resourcepack.Bedrock.class, Modpack.class);
    }

    @Override
    default boolean applicable(@NonNull Version version) {
      return Version.Bedrock.applicable(version);
    }

    /**
     * Canonical lookup aliases. Each delegates behavior to its concrete record,
     * but remains a distinct value for equality and label matching.
     */
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true)
    enum Of implements Bedrock {

      /**
       * Canonical alias of {@link Bedrock.Native}.
       */
      BEDROCK_NATIVE(new Bedrock.Native()),

      /**
       * Canonical alias of {@link Bedrock.Endstone}.
       */
      ENDSTONE(new Bedrock.Endstone()),
      /**
       * Canonical alias of {@link Bedrock.PowerNukkitX}.
       */
      POWER_NUKKITX(new Bedrock.PowerNukkitX());

      @NonNull
      Distribution distribution;

      @Override
      public String id() {
        return distribution.id();
      }

      @Override
      public Set<Environment> environments() {
        return distribution.environments();
      }

      @Override
      public Set<Class<? extends Artifact>> capabilities() {
        return distribution.capabilities();
      }

      @Override
      public boolean applicable(@NonNull Version version) {
        return distribution.applicable(version);
      }

      @Override
      public Artifact.Layout layout(@NonNull Artifact artifact, @NonNull Artifact.Context context) throws IOException {
        return distribution.layout(artifact, context);
      }
    }

    /**
     * Native runtime family; exact releases are provider-resolved.
     */
    record Native() implements Distribution.Bedrock {

      @Override
      public String id() {
        return "bedrock:native";
      }
    }

    /**
     * Endstone runtime family; exact releases are provider-resolved.
     */
    record Endstone() implements Manager {

      @Override
      public String id() {
        return "bedrock:endstone";
      }
    }

    /**
     * PowerNukkitX runtime family; exact releases are provider-resolved.
     */
    record PowerNukkitX() implements Manager {

      @Override
      public String id() {
        return "bedrock:powernukkitx";
      }
    }

    /**
     * Bedrock plugin manager servers.
     */
    sealed interface Manager extends Distribution.Bedrock {

      @Override
      default Set<Environment> environments() {
        return Set.of(Environment.SERVER);
      }

      @Override
      default Set<Class<? extends Artifact>> capabilities() {
        return Set.of(Plugin.class, Resourcepack.Bedrock.class, Modpack.class);
      }
    }
  }
}
