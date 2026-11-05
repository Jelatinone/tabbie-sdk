package cat.tabbie.sdk.minecraft.distribution;

import java.util.Set;

import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Bedrock runtime families, registered as the group {@code bedrock:base} of
 * every Bedrock distribution. Game versions and concrete runtime releases are
 * described and resolved separately from these stateless family values.
 */
@Registered("bedrock:base")
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
   * Server-only Bedrock runtimes that load plugins, registered as the group
   * {@code bedrock:manager}. Which of them also load behaviour and resource
   * packs, and how, the pack families' installs declare.
   */
  @Registered("bedrock:manager")
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
