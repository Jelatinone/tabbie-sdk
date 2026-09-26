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
  record Native() implements Bedrock {

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
  sealed interface Manager extends Bedrock {

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
