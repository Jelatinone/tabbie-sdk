package cat.tabbie.sdk.meta.action;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.meta.attribute.Applies;
import cat.tabbie.sdk.meta.attribute.Conditions;
import lombok.NonNull;

/**
 * One method contributing an action, and what its annotations say: the
 * subject it acts on, the targets it applies to, where it acts, and the key
 * its action is contributed by. The processors read one for each method while
 * a module compiles, holding the method's element; the registry reads one for
 * each method when it loads the module, holding the method as its action's
 * signature. Both read the same annotations into the same {@link Conditions}.
 *
 * @param <Subject>  how the subject is held: its element while compiling, its
 *                   class once loaded
 * @param <Call>     how the method is held: its element while compiling, its
 *                   action's signature calling it once loaded
 * @param name       the method as written, with the type declaring it, such as
 *                   {@code Mod.installRoot(Install.Context)}
 * @param subject    the type it acts on
 * @param conditions the targets it applies to
 * @param closure    where it acts
 * @param key        the value of its action's {@link Action#key() key}
 *                   element, or empty for an action keyed by nothing
 * @param call       the method
 */
public record Contributor<Subject, Call>(
    @NonNull String name,
    @NonNull Subject subject,
    @NonNull Conditions conditions,
    @NonNull Closure closure,
    @NonNull Optional<String> key,
    @NonNull Call call) {

  /**
   * Checks every part is given.
   */
  public Contributor {
  }

  /**
   * Reads a method contributing an action, as the registry does when it
   * loads a module: the type it acts on, from the action's {@code subject}
   * element or else the type declaring the method; where it acts, from the
   * action's {@code value} element, when it declares one; its key; the
   * targets it applies to, from its own {@link Applies} declarations within
   * its type's, within each enclosing type's outwards; and the method, as its
   * action's signature.
   *
   * @param <Call>    the action's signature
   * @param method    method carrying the action
   * @param action    the action, as the method carries it
   * @param signature the action's signature
   * @param lookup    lookup reaching the method, as the module's registration
   *                  provides it
   * @return the contributor
   * @throws IllegalArgumentException when the annotation is not an action, or
   *                                  a declaration is malformed, which the
   *                                  processors rule out while the module
   *                                  compiles
   * @throws IllegalStateException    when the lookup cannot reach the method
   */
  public static <Call> Contributor<Class<?>, Call> of(
      @NonNull Method method,
      @NonNull Annotation action,
      @NonNull Class<Call> signature,
      @NonNull MethodHandles.Lookup lookup) {
    Action marked = Optional.ofNullable(action.annotationType().getAnnotation(Action.class))
        .orElseThrow(() -> new IllegalArgumentException(String.format("@%s is not an action.",
            action.annotationType().getSimpleName())));
    Class<?> subject = element(action, "subject")
        .map(Class[].class::cast)
        .filter(subjects -> subjects.length > 0)
        .<Class<?>>map(subjects -> subjects[0])
        .orElse(method.getDeclaringClass());
    Closure closure = element(action, "value")
        .filter(Closure.class::isInstance)
        .map(Closure.class::cast)
        .orElse(Closure.ROOT);
    Optional<String> key = marked.key().isEmpty()
        ? Optional.empty()
        : element(action, marked.key()).map(Contributor::keyed);
    Conditions conditions = Conditions.of(Stream.iterate((AnnotatedElement) method, Objects::nonNull,
        element -> element instanceof Method declared
            ? declared.getDeclaringClass()
            : ((Class<?>) element).getEnclosingClass())
        .map(element -> List.of(element.getAnnotationsByType(Applies.class)))
        .toList()
        .reversed());
    try {
      Call call = MethodHandleProxies.asInterfaceInstance(signature, lookup.unreflect(method));
      return new Contributor<>(name(method), subject, conditions, closure, key, call);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(String.format("%s cannot be reached through its module's registration.",
          name(method)), exception);
    }
  }

  /**
   * Writes the value an action is keyed by as text, as the processors write
   * it: a class by its name, anything else as it prints.
   *
   * @param value the value of the action's key element
   * @return the key
   */
  public static String keyed(@NonNull Object value) {
    return value instanceof Class<?> type ? type.getName() : String.valueOf(value);
  }

  /**
   * Returns the value of an action's element, as written or by default.
   */
  private static Optional<Object> element(Annotation action, String name) {
    try {
      return Optional.of(action.annotationType().getMethod(name).invoke(action));
    } catch (NoSuchMethodException exception) {
      return Optional.empty();
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new IllegalStateException(String.format("@%s's %s cannot be read.",
          action.annotationType().getSimpleName(), name), exception);
    }
  }

  /**
   * Names a method as written, as the processors name it.
   */
  private static String name(Method method) {
    return String.format("%s.%s(%s)", name(method.getDeclaringClass()), method.getName(),
        Stream.of(method.getParameterTypes())
            .map(Contributor::name)
            .collect(Collectors.joining(", ")));
  }

  /**
   * Names a class by its simple name within the classes enclosing it.
   */
  private static String name(Class<?> type) {
    return type.getEnclosingClass() == null
        ? type.getSimpleName()
        : String.format("%s.%s", name(type.getEnclosingClass()), type.getSimpleName());
  }
}
