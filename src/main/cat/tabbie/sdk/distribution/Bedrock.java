package cat.tabbie.sdk.distribution;

import java.util.Set;

import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Version;
import lombok.NonNull;

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
}
