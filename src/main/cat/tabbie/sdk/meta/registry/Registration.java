package cat.tabbie.sdk.meta.registry;

import java.util.List;
import java.util.Set;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.minecraft.distribution.Distribution;
import lombok.NonNull;

/**
 * What one module registers with tabbie: the distributions and content
 * families its {@link Registered} types declare. The
 * {@link Registered.Processor processor} generates one in each module that
 * registers anything, and the module provides it as a service; core finds
 * every module's with {@link java.util.ServiceLoader} and merges them into a
 * {@link cat.tabbie.sdk.Registry}. A module never implements it by hand.
 */
public interface Registration {

  /**
   * Returns the version of the SDK the module was compiled against, so core
   * can refuse a module built for another.
   *
   * @return SDK version, such as {@code 0.1.0}
   */
  @NonNull
  String sdk();

  /**
   * Returns the distributions the module registers: every value of each of
   * its registered enums.
   *
   * @return distributions, in the order they are declared, empty when it
   *         registers none
   */
  @NonNull
  List<Distribution> distributions();

  /**
   * Returns the content families the module registers.
   *
   * @return families, empty when it registers none
   */
  @NonNull
  Set<Class<? extends Artifact>> families();
}
