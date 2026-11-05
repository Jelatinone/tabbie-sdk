package cat.tabbie.sdk.distribution;

import java.util.Set;

import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Version;
import lombok.NonNull;

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
}
