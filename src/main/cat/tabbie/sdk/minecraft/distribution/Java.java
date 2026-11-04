package cat.tabbie.sdk.minecraft.distribution;

import java.util.Set;

import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Java runtime families, registered as the group {@code java:base} of every
 * Java distribution. Game versions and concrete runtime releases are
 * described and resolved separately from these stateless family values.
 */
@Registered("java:base")
public interface Java extends Distribution {

  @Override
  default Set<Environment> environments() {
    return Set.of(Environment.CLIENT, Environment.SERVER);
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
   * Java loaders supporting mods on clients and dedicated servers, registered
   * as the group {@code java:launcher}.
   */
  @Registered("java:launcher")
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
  }

  /**
   * Server-only Java runtimes that load plugins, registered as the group
   * {@code java:manager}.
   */
  @Registered("java:manager")
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
  }
}
