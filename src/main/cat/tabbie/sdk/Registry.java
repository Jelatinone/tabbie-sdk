package cat.tabbie.sdk;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.meta.action.Contribution;
import cat.tabbie.sdk.meta.action.Install;
import cat.tabbie.sdk.meta.registry.Contributions;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.meta.registry.Registration;
import cat.tabbie.sdk.minecraft.distribution.Distribution;
import lombok.NonNull;

/**
 * Everything tabbie knows of, read from every {@link Registration}: the
 * distributions and groups of them the SDK and each loaded extension
 * register, in a {@link Distribution.Registry}, and the content families and
 * installs, in an {@link Artifact.Registry}. A registry is read once, when
 * core loads its modules, and never changes.
 *
 * <p>
 * Each action's contributions are chosen between by precedence, as
 * {@link Contributions} describes. The registry gathers every action's
 * {@link #replacements() replacements}, so core can show which module changes
 * what, and {@link #clashes() clashes}, so core can refuse to start while
 * there is one.
 *
 * @param registrations registrations, in the order they were found
 * @param distributions every registered distribution and group of them
 * @param artifacts     every registered content family, and the installs
 *                      contributed for them
 */
public record Registry(
    @NonNull List<Registration> registrations,
    @NonNull Distribution.Registry distributions,
    @NonNull Artifact.Registry artifacts) {

  /**
   * Copies the registrations.
   */
  public Registry {
    registrations = List.copyOf(registrations);
  }

  /**
   * Reads registrations into a registry: what each listed type is, from its
   * {@link Registered} annotation, and the contributions its methods make,
   * each with the module declaring it.
   *
   * @param registrations registrations, in the order they were found
   * @return the registry
   * @throws IllegalArgumentException when a family is registered twice, two
   *                                  distributions or groups share an
   *                                  identifier, or a registered type is
   *                                  none of what may be registered
   * @throws IllegalStateException    when a registration's lookup cannot
   *                                  reach one of its contributions
   */
  public static Registry of(@NonNull List<Registration> registrations) {
    List<Distribution> distributions = new ArrayList<>();
    Map<String, Class<? extends Distribution>> groups = new LinkedHashMap<>();
    Set<Class<? extends Artifact>> families = new HashSet<>();
    List<Contributions.Contributed<Install.Signature>> installs = new ArrayList<>();
    for (Registration registration : registrations) {
      for (Class<?> type : registration.types()) {
        Optional.ofNullable(type.getAnnotation(Registered.class))
            .ifPresent(registered -> register(type, registered.value(), distributions, groups, families));
        Stream.of(type.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Install.class))
            .map(method -> install(method, type, registration.lookup()))
            .forEach(installs::add);
      }
    }
    Distribution.Registry vocabulary = new Distribution.Registry(distributions, groups);
    return new Registry(registrations, vocabulary, new Artifact.Registry(vocabulary, families,
        new Contributions<>(installs, vocabulary.names())));
  }

  /**
   * Loads every registration {@link ServiceLoader} finds where the SDK is
   * loaded: the SDK's own, and those of the modules beside it.
   *
   * @return the registry
   * @throws IllegalArgumentException as {@link #of(List)} does
   */
  public static Registry load() {
    return of(ServiceLoader.load(Registration.class).stream()
        .map(ServiceLoader.Provider::get)
        .toList());
  }

  /**
   * Loads every registration in a module layer and the layers it builds on,
   * as core does for the extensions it loads into layers of their own.
   *
   * @param layer layer to load from
   * @return the registry
   * @throws IllegalArgumentException as {@link #of(List)} does
   */
  public static Registry load(@NonNull ModuleLayer layer) {
    return of(ServiceLoader.load(layer, Registration.class).stream()
        .map(ServiceLoader.Provider::get)
        .toList());
  }

  /**
   * Lists where one module's contribution is chosen over another module's,
   * for every action.
   *
   * @return replacements, empty when no two modules' contributions meet
   */
  public List<Contributions.Replacement> replacements() {
    return artifacts.installs().replacements();
  }

  /**
   * Lists every clash, for every action. Core refuses to start while there is
   * one, naming every contribution in it.
   *
   * @return clashes, empty when precedence decides every meeting
   */
  public List<Contributions.Clash> clashes() {
    return artifacts.installs().clashes();
  }

  /**
   * Registers one type as what it is: every value of an enum of
   * distributions, a group, or a family.
   */
  private static void register(
      Class<?> type,
      String group,
      List<Distribution> distributions,
      Map<String, Class<? extends Distribution>> groups,
      Set<Class<? extends Artifact>> families) {
    if (type.isEnum() && Distribution.class.isAssignableFrom(type)) {
      Stream.of(type.getEnumConstants())
          .map(Distribution.class::cast)
          .forEach(distributions::add);
    } else if (Distribution.class.isAssignableFrom(type)) {
      if (groups.putIfAbsent(group, type.asSubclass(Distribution.class)) != null) {
        throw new IllegalArgumentException(String.format("Two groups are registered as %s.", group));
      }
    } else if (Artifact.class.isAssignableFrom(type)) {
      if (!families.add(type.asSubclass(Artifact.class))) {
        throw new IllegalArgumentException(String.format("%s is registered twice.", type.getName()));
      }
    } else {
      throw new IllegalArgumentException(String.format("%s is registered, but is neither an enum or abstract type "
          + "of distributions nor a content family.", type.getName()));
    }
  }

  /**
   * Reads one install: the type it installs, the one it names or else the
   * type declaring it, where it acts, the method as an install's signature,
   * and the module declaring it.
   */
  private static Contributions.Contributed<Install.Signature> install(
      Method method,
      Class<?> type,
      MethodHandles.Lookup lookup) {
    Install install = method.getAnnotation(Install.class);
    Class<?> subject = install.subject().length == 0 ? type : install.subject()[0];
    return new Contributions.Contributed<>(method.getDeclaringClass().getModule(),
        Contribution.of(method, subject, install.value(), Install.Signature.class, lookup));
  }
}
