package cat.tabbie.sdk.minecraft.distribution;

import java.util.Set;

import cat.tabbie.sdk.addon.artifact.*;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Java runtime families. Game versions and concrete runtime releases are
 * described and resolved separately from these stateless family values.
 */
public interface Java extends Distribution {

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
   * Unmodified Java Edition runtimes.
   */
  @Registered
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  enum Of implements Java {

    /**
     * Vanilla
     */
    VANILLA("java:vanilla");

    String id;

    /**
     * Of constructor
     *
     * @param id identifier
     */
    Of(String id) {
      Distribution.validate(id);
      this.id = id;
    }

    @Override
    public String id() {
      return id;
    }
  }

  /**
   * Java loaders supporting mods on clients and dedicated servers.
   */
  interface Launcher extends Java {

    /**
     * Canonical mod-loader values.
     */
    @Registered
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Of implements Launcher {

      /**
       * Fabric
       */
      FABRIC("java:fabric"),

      /**
       * Quilt
       */
      QUILT("java:quilt"),

      /**
       * Forge
       */
      FORGE("java:forge"),

      /**
       * Neo-Forge
       */
      NEO_FORGE("java:neoforge");

      String id;

      /**
       * Of constructor
       *
       * @param id identifier
       */
      Of(String id) {
        Distribution.validate(id);
        this.id = id;
      }

      @Override
      public String id() {
        return id;
      }

    }

    @Override
    default Set<Class<? extends Artifact>> capabilities() {
      return Set.of(Mod.class, Datapack.class, Resourcepack.Java.class, Modpack.class);
    }
  }

  /**
   * Server-only Java runtimes that load plugins.
   */
  interface Manager extends Java {

    /**
     * Canonical plugin-server values.
     */
    @Registered
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Of implements Manager {

      /**
       * Craft-Bukkit
       */
      CRAFT_BUKKIT("java:craftbukkit"),

      /**
       * Spigot
       */
      SPIGOT("java:spigot"),

      /**
       * Paper
       */
      PAPER("java:paper"),

      /**
       * Purpur
       */
      PURPUR("java:purpur"),

      /**
       * Folia
       */
      FOLIA("java:folia");

      String id;

      /**
       * Of constructor
       *
       * @param id identifier
       */
      Of(String id) {
        Distribution.validate(id);
        this.id = id;
      }

      @Override
      public String id() {
        return id;
      }

    }

    @Override
    default Set<Environment> environments() {
      return Set.of(Environment.SERVER);
    }

    @Override
    default Set<Class<? extends Artifact>> capabilities() {
      return Set.of(Plugin.class, Datapack.class, Resourcepack.Java.class);
    }
  }
}
