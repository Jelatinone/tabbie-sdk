package cat.tabbie.sdk.meta.action;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.meta.attribute.Conditions;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.attribute.Environments;
import cat.tabbie.sdk.meta.attribute.Platforms;
import cat.tabbie.sdk.meta.attribute.Versions;
import lombok.NonNull;

/**
 * One method contributing an action, and what its annotations say: the
 * subject it acts on, the targets it applies to, and where it acts. The
 * processors read one for each method while a module compiles, holding the
 * method's element; the registry reads one for each method when it loads the
 * module, holding the method as its action's signature. Both read the same
 * annotations into the same {@link Conditions}.
 *
 * @param <Subject>   how the subject is held: its element while compiling, its
 *                    class once loaded
 * @param <Call>      how the method is held: its element while compiling, its
 *                    action's signature calling it once loaded
 * @param contributor the method as written, with the type declaring it, such
 *                    as {@code Mod.installRoot(Install.Context)}
 * @param subject     the type it acts on
 * @param conditions  the targets it applies to
 * @param closure     where it acts
 * @param call        the method
 */
public record Contribution<Subject, Call>(
    @NonNull String contributor,
    @NonNull Subject subject,
    @NonNull Conditions conditions,
    @NonNull Closure closure,
    @NonNull Call call) {

  /**
   * Checks every part is given.
   */
  public Contribution {
  }

  /**
   * Reads a method contributing an action, as the registry does when it
   * loads a module: each attribute from its nearest declaration, the method's
   * own first, then its type's, then each enclosing type's outwards; and the
   * method, as its action's signature.
   *
   * @param <Call>    the action's signature
   * @param method    method carrying the action
   * @param subject   the type it acts on
   * @param closure   where it acts
   * @param signature the action's signature
   * @param lookup    lookup reaching the method, as the module's registration
   *                  provides it
   * @return the contribution
   * @throws IllegalArgumentException when an attribute is malformed, which
   *                                  the processors rule out while the
   *                                  module compiles
   * @throws IllegalStateException    when the lookup cannot reach the method
   */
  public static <Call> Contribution<Class<?>, Call> of(
      @NonNull Method method,
      @NonNull Class<?> subject,
      @NonNull Closure closure,
      @NonNull Class<Call> signature,
      @NonNull MethodHandles.Lookup lookup) {
    Conditions conditions = Conditions.of(
        nearest(method, Versions.class).stream().findFirst(),
        nearest(method, Distributions.class).stream().findFirst(),
        nearest(method, Environments.class).stream().findFirst(),
        nearest(method, Platforms.class));
    try {
      Call call = MethodHandleProxies.asInterfaceInstance(signature, lookup.unreflect(method));
      return new Contribution<>(name(method), subject, conditions, closure, call);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(String.format("%s cannot be reached through its module's registration.",
          name(method)), exception);
    }
  }

  /**
   * Returns the nearest declaration of an attribute: on the method itself,
   * or else on the nearest type enclosing it.
   */
  private static <Attribute extends Annotation> List<Attribute> nearest(
      Method method,
      Class<Attribute> attribute) {
    return Stream.iterate((AnnotatedElement) method, Objects::nonNull,
        element -> element instanceof Method declared
            ? declared.getDeclaringClass()
            : ((Class<?>) element).getEnclosingClass())
        .map(element -> List.of(element.getAnnotationsByType(attribute)))
        .filter(found -> !found.isEmpty())
        .findFirst()
        .orElse(List.of());
  }

  /**
   * Names a method as written, as the processors name it.
   */
  private static String name(Method method) {
    return String.format("%s.%s(%s)", name(method.getDeclaringClass()), method.getName(),
        Stream.of(method.getParameterTypes())
            .map(Contribution::name)
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
