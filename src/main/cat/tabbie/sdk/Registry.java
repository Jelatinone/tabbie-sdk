package cat.tabbie.sdk;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.content.Artifact;
import cat.tabbie.sdk.content.Install;
import cat.tabbie.sdk.distribution.Distribution;
import cat.tabbie.sdk.meta.action.Action;
import cat.tabbie.sdk.meta.action.Contributor;
import cat.tabbie.sdk.meta.registry.Contribution;
import cat.tabbie.sdk.meta.registry.Contributions;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.meta.registry.Registrable;
import cat.tabbie.sdk.meta.registry.Registration;
import lombok.NonNull;

/**
 * Everything tabbie knows of, read from every {@link Registration}: the types
 * each loaded module registers, by the {@link Registrable registrable kind}
 * each is a type of, and the contributors to every action. The distributions
 * and groups of them are kept in a {@link Distribution.Registry}, and the
 * content families and their installs in an {@link Artifact.Registry}, typed
 * views over the same contributors. A registry is read once, when core loads
 * its modules, and never changes.
 *
 * <p>
 * Each action's contributors are chosen between by precedence, as
 * {@link Contributions} describes. The registry gathers every action's
 * {@link #replacements() replacements}, so core can show which module changes
 * what, and {@link #clashes() clashes}, so core can ask the user which
 * contributor to prefer.
 *
 * @param registrations registrations, in the order they were found
 * @param distributions every registered distribution and group of them
 * @param actions       every action's contributors, by the action's
 *                      annotation
 * @param artifacts     every registered content family, and the installs
 *                      contributed for them
 */
public record Registry(
    @NonNull List<Registration> registrations,
    @NonNull Distribution.Registry distributions,
    @NonNull Map<Class<? extends Annotation>, Contributions<?>> actions,
    @NonNull Artifact.Registry artifacts) {

  /**
   * Copies the registrations and the actions.
   */
  public Registry {
    registrations = List.copyOf(registrations);
    actions = Map.copyOf(actions);
  }

  /**
   * Reads registrations into a registry: what each listed type is, from its
   * {@link Registered} annotation and the registrable kind it is a type of,
   * and the contributors its methods make, to every action, each with the
   * module declaring it.
   *
   * @param registrations registrations, in the order they were found
   * @return the registry
   * @throws IllegalArgumentException when a type is registered twice, two
   *                                  values or groups of a kind share an
   *                                  identifier, or a registered type is of
   *                                  no registrable kind
   * @throws IllegalStateException    when a registration's lookup cannot
   *                                  reach one of its contributors
   */
  public static Registry of(@NonNull List<Registration> registrations) {
    Map<Class<?>, List<Object>> values = new LinkedHashMap<>();
    Map<Class<?>, Map<String, Class<?>>> groups = new LinkedHashMap<>();
    Map<Class<?>, Set<Class<?>>> types = new LinkedHashMap<>();
    Map<Class<? extends Annotation>, List<Contributions.Contributed<Object>>> contributed = new LinkedHashMap<>();
    for (Registration registration : registrations) {
      for (Class<?> type : registration.types()) {
        Optional.ofNullable(type.getAnnotation(Registered.class))
            .ifPresent(registered -> register(type, registered.value(), values, groups, types));
        for (Method method : type.getDeclaredMethods()) {
          for (Annotation annotation : method.getAnnotations()) {
            Optional.ofNullable(annotation.annotationType().getAnnotation(Action.class))
                .map(action -> contributed(method, annotation, action, registration.lookup()))
                .ifPresent(found -> contributed.computeIfAbsent(annotation.annotationType(), _ -> new ArrayList<>())
                    .add(found));
          }
        }
      }
    }
    Distribution.Registry vocabulary = new Distribution.Registry(
        values.getOrDefault(Distribution.class, List.of()).stream()
            .map(Distribution.class::cast)
            .toList(),
        of(groups.getOrDefault(Distribution.class, Map.of()), Distribution.class));
    Map<Class<? extends Annotation>, Contributions<?>> actions = new LinkedHashMap<>();
    contributed.forEach((action, found) -> actions.put(action, new Contributions<>(
        action.getAnnotation(Action.class).cardinality(), found, vocabulary.names())));
    Set<Class<? extends Artifact>> families = types.getOrDefault(Artifact.class, Set.of()).stream()
        .<Class<? extends Artifact>>map(family -> family.asSubclass(Artifact.class))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    return new Registry(registrations, vocabulary, actions, new Artifact.Registry(vocabulary, families,
        contributions(actions, vocabulary, Install.class, Install.Signature.class)));
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
   * Returns one action's contributors, typed by its signature.
   *
   * @param <Call>    the action's signature
   * @param action    the action's annotation
   * @param signature the signature its {@link Action} names
   * @return its contributors, none when no module contributes to it
   * @throws IllegalArgumentException when the annotation is not an action, or
   *                                  its signature is another
   */
  public <Call> Contributions<Call> contributions(
      @NonNull Class<? extends Annotation> action,
      @NonNull Class<Call> signature) {
    return contributions(actions, distributions, action, signature);
  }

  /**
   * Lists where one module's contributor is chosen over another module's, for
   * every action.
   *
   * @return replacements, empty when no two modules' contributors meet
   */
  public List<Contributions.Replacement> replacements() {
    return actions.values().stream()
        .flatMap(contributions -> contributions.replacements().stream())
        .toList();
  }

  /**
   * Lists every clash, for every action. Core shows each to the user, naming
   * every contributor in it, and the user's pick joins the preferences core
   * chooses with.
   *
   * @return clashes, empty when precedence decides every meeting
   */
  public List<Contribution.Clash<?>> clashes() {
    return actions.values().stream()
        .<Contribution.Clash<?>>flatMap(contributions -> contributions.clashes().stream())
        .toList();
  }

  /**
   * Returns one action's contributors, typed by its signature, from those read.
   */
  @SuppressWarnings("unchecked")
  private static <Call> Contributions<Call> contributions(
      Map<Class<? extends Annotation>, Contributions<?>> actions,
      Distribution.Registry distributions,
      Class<? extends Annotation> action,
      Class<Call> signature) {
    Action marked = Optional.ofNullable(action.getAnnotation(Action.class))
        .filter(found -> found.value() == signature)
        .orElseThrow(() -> new IllegalArgumentException(String.format("@%s is not an action whose signature is %s.",
            action.getSimpleName(), signature.getName())));
    Contributions<?> found = actions.get(action);
    return found == null
        ? new Contributions<>(marked.cardinality(), List.of(), distributions.names())
        : (Contributions<Call>) found;
  }

  /**
   * Reads one method's contribution to one action, with the module declaring
   * it.
   */
  @SuppressWarnings("unchecked")
  private static Contributions.Contributed<Object> contributed(
      Method method,
      Annotation annotation,
      Action action,
      MethodHandles.Lookup lookup) {
    return new Contributions.Contributed<>(method.getDeclaringClass().getModule(),
        Contributor.of(method, annotation, (Class<Object>) action.value(), lookup));
  }

  /**
   * Registers one type as what its kind says it is: every value of an enum of
   * the kind, or a group of them, or the type itself.
   */
  private static void register(
      Class<?> type,
      String group,
      Map<Class<?>, List<Object>> values,
      Map<Class<?>, Map<String, Class<?>>> groups,
      Map<Class<?>, Set<Class<?>>> types) {
    Class<?> kind = kind(type).orElseThrow(() -> new IllegalArgumentException(String.format(
        "%s is registered, but is a type of no registrable kind.", type.getName())));
    switch (kind.getAnnotation(Registrable.class).value()) {
      case VALUES -> {
        if (type.isEnum()) {
          values.computeIfAbsent(kind, _ -> new ArrayList<>()).addAll(List.of(type.getEnumConstants()));
        } else if (groups.computeIfAbsent(kind, _ -> new LinkedHashMap<>()).putIfAbsent(group, type) != null) {
          throw new IllegalArgumentException(String.format("Two groups are registered as %s.", group));
        }
      }
      case TYPES -> {
        if (!types.computeIfAbsent(kind, _ -> new LinkedHashSet<>()).add(type)) {
          throw new IllegalArgumentException(String.format("%s is registered twice.", type.getName()));
        }
      }
    }
  }

  /**
   * Finds the registrable kind a type is a type of: the nearest of its
   * supertypes marked {@link Registrable}, itself excluded.
   */
  private static Optional<Class<?>> kind(Class<?> type) {
    Deque<Class<?>> pending = new ArrayDeque<>(supertypes(type).toList());
    Set<Class<?>> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      Class<?> next = pending.removeFirst();
      if (!seen.add(next)) {
        continue;
      }
      if (next.isAnnotationPresent(Registrable.class)) {
        return Optional.of(next);
      }
      supertypes(next).forEach(pending::addLast);
    }
    return Optional.empty();
  }

  /**
   * Lists a type's direct supertypes.
   */
  private static Stream<Class<?>> supertypes(Class<?> type) {
    return Stream.concat(Stream.ofNullable(type.getSuperclass()), Stream.of(type.getInterfaces()));
  }

  /**
   * Types a kind's groups by the kind.
   */
  private static <Kind> Map<String, Class<? extends Kind>> of(Map<String, Class<?>> groups, Class<Kind> kind) {
    Map<String, Class<? extends Kind>> typed = new LinkedHashMap<>();
    groups.forEach((id, group) -> typed.put(id, group.asSubclass(kind)));
    return typed;
  }
}
