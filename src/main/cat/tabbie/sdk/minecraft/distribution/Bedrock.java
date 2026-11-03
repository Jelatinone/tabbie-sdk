package cat.tabbie.sdk.minecraft.distribution;

import java.util.Set;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Behaviourpack;
import cat.tabbie.sdk.addon.artifact.Modpack;
import cat.tabbie.sdk.addon.artifact.Plugin;
import cat.tabbie.sdk.addon.artifact.Resourcepack;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Bedrock runtime families. Game versions and concrete runtime releases are
 * described and resolved separately from these stateless family values.
 */
public interface Bedrock extends Distribution {

  /**
   * Supports clients and dedicated servers unless a family narrows it.
   *
   * @return immutable environments
   */
  @Override
  default Set<Environment> environments() {
    return Set.of(Environment.CLIENT, Environment.SERVER);
  }

  /**
   * Accepts behaviour packs, Bedrock resource packs, and modpacks unless a
   * family declares otherwise.
   *
   * @return immutable capabilities
   */
  @Override
  default Set<Class<? extends Artifact>> capabilities() {
    return Set.of(Behaviourpack.class, Resourcepack.Bedrock.class, Modpack.class);
  }

  /**
   * Accepts Bedrock Edition game versions unless a family narrows them.
   *
   * @param version discovered game version
   * @return whether the version belongs to Bedrock Edition
   */
  @Override
  default boolean applicable(@NonNull Version version) {
    return Version.Bedrock.applicable(version);
  }

  /**
   * Unmodified Bedrock Edition runtimes.
   */
  @Registered
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  enum Of implements Bedrock {

    /**
     * Vanilla
     */
    VANILLA("bedrock:vanilla");

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

    /**
     * Returns the stable, case-sensitive namespaced identifier.
     *
     * @return identifier such as {@code bedrock:vanilla}
     */
    @Override
    public String id() {
      return id;
    }
  }

  /**
   * Server-only Bedrock runtimes that load plugins in place of behaviour packs.
   */
  interface Manager extends Bedrock {

    /**
     * Supports dedicated servers only.
     *
     * @return immutable environments
     */
    @Override
    default Set<Environment> environments() {
      return Set.of(Environment.SERVER);
    }

    /**
     * Accepts plugins, Bedrock resource packs, and modpacks.
     *
     * @return immutable capabilities
     */
    @Override
    default Set<Class<? extends Artifact>> capabilities() {
      return Set.of(Plugin.class, Resourcepack.Bedrock.class, Modpack.class);
    }

    /**
     * Canonical plugin-server values.
     */
    @Registered
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Of implements Manager {

      /**
       * Endstone
       */
      ENDSTONE("bedrock:endstone"),

      /**
       * Cloudburst-server
       */
      CLOUDBURST("bedrock:cloudburst"),

      /**
       * Cloudburst-nukkit
       */
      CLOUDBURST_NUKKIT("bedrock:cloudburst-nukkit"),

      /**
       * Nukkit-MOT
       */
      MOT_NUKKIT("bedrock:nukkit-mot"),

      /**
       * Power-Nukkit-X
       */
      POWER_NUKKIT("bedrock:power-nukkit");

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

      /**
       * Returns the stable, case-sensitive namespaced identifier.
       *
       * @return identifier such as {@code bedrock:endstone}
       */
      @Override
      public String id() {
        return id;
      }
    }
  }
}
