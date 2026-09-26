package cat.tabbie.sdk.minecraft.distribution;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.addon.artifact.*;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

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
  record Fabric() implements Java.Launcher {

    @Override
    public String id() {
      return "java:fabric";
    }
  }

  /**
   * Quilt runtime family; exact releases are provider-resolved.
   */
  record Quilt() implements Java.Launcher {

    @Override
    public String id() {
      return "java:quilt";
    }
  }

  /**
   * Forge runtime family; exact releases are provider-resolved.
   */
  record Forge() implements Java.Launcher {
    @Override
    public String id() {
      return "java:forge";
    }
  }

  /**
   * NeoForge runtime family; exact releases are provider-resolved.
   */
  record NeoForge() implements Java.Launcher {
    @Override
    public String id() {
      return "java:neoforge";
    }
  }

  /**
   * CraftBukkit runtime family; exact releases are provider-resolved.
   */
  record CraftBukkit() implements Java.Manager {
    @Override
    public String id() {
      return "java:craftbukkit";
    }
  }

  /**
   * Spigot runtime family; exact releases are provider-resolved.
   */
  record Spigot() implements Java.Manager {

    @Override
    public String id() {
      return "java:spigot";
    }
  }

  /**
   * Paper runtime family; exact releases are provider-resolved.
   */
  record Paper() implements Java.Manager {

    @Override
    public String id() {
      return "java:paper";
    }
  }

  /**
   * Purpur runtime family; exact releases are provider-resolved.
   */
  record Purpur() implements Java.Manager {

    @Override
    public String id() {
      return "java:purpur";
    }
  }

  /**
   * Folia runtime family; exact releases are provider-resolved.
   */
  record Folia() implements Java.Manager {

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
