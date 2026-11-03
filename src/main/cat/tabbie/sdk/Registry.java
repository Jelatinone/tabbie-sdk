package cat.tabbie.sdk;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.meta.registry.Registration;
import cat.tabbie.sdk.minecraft.distribution.Distribution;
import lombok.NonNull;

/**
 * Every {@link Registration} tabbie knows of, merged: the distributions and
 * content families the SDK and each loaded extension register. Lookups by
 * identifier go through a registry rather than a global list, so code sees
 * exactly what core has loaded. A registry is checked when it is built, and
 * never changes.
 *
 * @param registrations registrations, in the order they were found
 */
public record Registry(@NonNull List<Registration> registrations) {

  /**
   * Copies the registrations, and checks that no two distributions share an
   * identifier and no family is registered twice.
   *
   * @throws IllegalArgumentException when two distributions share an
   *                                  identifier, or a family is registered
   *                                  twice
   */
  public Registry {
    registrations = List.copyOf(registrations);
    Set<String> ids = new HashSet<>();
    registrations.stream()
        .flatMap(registration -> registration.distributions().stream())
        .map(Distribution::id)
        .filter(id -> !ids.add(id))
        .findFirst()
        .ifPresent(id -> {
          throw new IllegalArgumentException(String.format("Two distributions are registered as %s.", id));
        });
    Set<Class<? extends Artifact>> families = new HashSet<>();
    registrations.stream()
        .flatMap(registration -> registration.families().stream())
        .filter(family -> !families.add(family))
        .findFirst()
        .ifPresent(family -> {
          throw new IllegalArgumentException(String.format("%s is registered twice.", family.getName()));
        });
  }

  /**
   * Loads every registration {@link ServiceLoader} finds where the SDK is
   * loaded: the SDK's own, and those of the modules beside it.
   *
   * @return the registry
   * @throws IllegalArgumentException as the canonical constructor does
   */
  public static Registry load() {
    return new Registry(ServiceLoader.load(Registration.class).stream()
        .map(ServiceLoader.Provider::get)
        .toList());
  }

  /**
   * Loads every registration in a module layer and the layers it builds on,
   * as core does for the extensions it loads into layers of their own.
   *
   * @param layer layer to load from
   * @return the registry
   * @throws IllegalArgumentException as the canonical constructor does
   */
  public static Registry load(@NonNull ModuleLayer layer) {
    return new Registry(ServiceLoader.load(layer, Registration.class).stream()
        .map(ServiceLoader.Provider::get)
        .toList());
  }

  /**
   * Lists every registered distribution.
   *
   * @return distributions, in registration order
   */
  public List<Distribution> distributions() {
    return registrations.stream()
        .flatMap(registration -> registration.distributions().stream())
        .toList();
  }

  /**
   * Finds a registered distribution by identifier, for example when reading
   * a persisted lock.
   *
   * @param id case-sensitive identifier such as {@code java:fabric}
   * @return the distribution, or empty when none is registered so
   */
  public Optional<Distribution> distribution(@NonNull String id) {
    return distributions().stream()
        .filter(distribution -> distribution.id().equals(id))
        .findFirst();
  }

  /**
   * Lists every registered content family.
   *
   * @return families, in registration order
   */
  public Set<Class<? extends Artifact>> families() {
    return registrations.stream()
        .flatMap(registration -> registration.families().stream())
        .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new),
            Collections::unmodifiableSet));
  }
}
