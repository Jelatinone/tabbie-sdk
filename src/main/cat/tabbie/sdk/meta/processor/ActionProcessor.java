package cat.tabbie.sdk.meta.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import cat.tabbie.sdk.meta.action.Action;
import cat.tabbie.sdk.meta.action.Closure;
import cat.tabbie.sdk.meta.action.Contributor;
import cat.tabbie.sdk.meta.action.Coverage;
import cat.tabbie.sdk.meta.action.Key;
import cat.tabbie.sdk.meta.attribute.Applies;
import cat.tabbie.sdk.meta.attribute.Subject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

/**
 * Checks every action, and every contribution to one, a module compiles.
 *
 * <p>
 * An action, when its contributions may act in different places, declares
 * where each acts, {@code Closure value()}; without one, every contribution
 * acts in the root. It marks at most one element {@link Key}. Its
 * {@link Action} names its signature: a functional interface whose one method
 * takes the subject, then the action's context, or, for an action acting on
 * types, only its context.
 *
 * <p>
 * Each method carrying an action is public and has its action's signature:
 * an instance method takes the context and acts on the type declaring it; a
 * static one takes the type it acts on, then the context. A contribution to
 * an action acting on types is static and takes only the context, acting on
 * the type declaring it or on the one its {@link Subject} names; no other
 * contribution names one. Each returns what the signature returns, and throws
 * nothing more. The type it acts on is one the signature takes, and it and
 * the type declaring the method are public, as is every type enclosing them,
 * and the package declaring the method is exported, so the registry reaches
 * the method through a public lookup. The contributions of each action to
 * each subject within a type are checked together as a {@link Coverage}, by
 * key, each conflict reported on the later of its two methods. An
 * {@link Applies} or {@link Subject} declaration on a method carrying no
 * action, an {@link Applies} on a type with no action within it, or a
 * {@link Key} on an element of anything but an action, is an error, since
 * nothing would read it, as is a malformed declaration, reported on each
 * method it reaches.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ActionProcessor extends Processor {

  /**
   * Creates the processor, as javac does when it finds it on the processor
   * path. An action is any annotation marked as one, so it reads every
   * annotation.
   */
  public ActionProcessor() {
    super(Set.of("*"));
  }

  @Override
  protected void round(RoundEnvironment round) {
    nested(round.getRootElements()).forEach(this::check);
  }

  /**
   * Checks one type: the action it is, if it is one, its methods carrying
   * actions, and their contributions together.
   */
  private void check(TypeElement type) {
    if (type.getKind() == ElementKind.ANNOTATION_TYPE && mirror(type, Action.class).isPresent()) {
      shape(type);
    } else {
      keys(type).forEach(element -> error(element, """
          %s.%s() is marked @Key, but %s is no action; @Key only marks the element an action's contributors are \
          keyed by.""", type.getSimpleName(), element.getSimpleName(), type.getSimpleName()));
    }
    if (attributed(type) && nested(List.of(type))
        .flatMap(inner -> ElementFilter.methodsIn(inner.getEnclosedElements()).stream())
        .allMatch(method -> actions(method).isEmpty())) {
      error(type, """
          %s declares @Applies but no action within it; @Applies only says which targets an action applies \
          to.""", type.getSimpleName());
    }
    Map<Covered, List<Contributor<TypeElement, ExecutableElement>>> coverages = new LinkedHashMap<>();
    boolean contributes = false;
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      List<? extends AnnotationMirror> actions = actions(method);
      if (actions.isEmpty()) {
        if (attributed(method)) {
          error(method, "%s declares @Applies but no action; @Applies only says which targets an action applies to.",
              name(method));
        }
        if (mirror(method, Subject.class).isPresent()) {
          error(method, "%s declares @Subject but no action; @Subject only names what a contribution acts on.",
              name(method));
        }
        continue;
      }
      contributes = true;
      if (!method.getModifiers().contains(Modifier.PUBLIC)) {
        error(method, "%s is a contribution, so it is public: the registry calls it.", name(method));
        continue;
      }
      for (AnnotationMirror action : actions) {
        try {
          Contributor<TypeElement, ExecutableElement> contributor = contributor(method, action);
          signed(method, action, contributor.subject());
          coverages.computeIfAbsent(new Covered(written(action), contributor.subject(), contributor.key()),
              _ -> new ArrayList<>()).add(contributor);
        } catch (IllegalArgumentException exception) {
          error(method, "%s", exception.getMessage());
        }
      }
    }
    coverages.forEach((covered, contributions) -> new Coverage<>(covered.action(), contributions).conflicts()
        .forEach(conflict -> error(conflict.later().call(), "%s", conflict.message())));
    if (contributes && !exported(type)) {
      error(type, """
          %s declares contributions, so module %s exports %s to every module: the registry calls them through a \
          public lookup.""", type.getSimpleName(), elements.getModuleOf(type).getQualifiedName(),
          elements.getPackageOf(type).getQualifiedName());
    }
  }

  /**
   * Checks an action declares what every action does.
   */
  private void shape(TypeElement action) {
    Map<String, TypeMirror> elements = ElementFilter.methodsIn(action.getEnclosedElements()).stream()
        .collect(Collectors.toMap(element -> element.getSimpleName().toString(), ExecutableElement::getReturnType));
    // An action acting only in the root declares no closure
    if (elements.containsKey("value") && !same(elements.get("value"), Closure.class)) {
      error(action, """
          @%s is an action, so its value() is a Closure, where its contributions act; an action acting only in the \
          root declares none.""", action.getSimpleName());
    }
    List<ExecutableElement> keys = keys(action);
    if (keys.size() > 1) {
      error(action, "@%s marks %s with @Key; an action's contributors are keyed by one element at most.",
          action.getSimpleName(), keys.stream()
              .map(key -> "%s()".formatted(key.getSimpleName()))
              .collect(Collectors.joining(", ")));
    }
    if (signature(action).flatMap(ActionProcessor::method).filter(ActionProcessor::shaped).isEmpty()) {
      error(action, """
          @%s is an action, so its signature is a functional interface whose one method takes the subject it acts \
          on, then its context, or only its context for an action acting on types.""", action.getSimpleName());
    }
  }

  /**
   * Checks a contribution has its action's signature, reporting the first way
   * it does not.
   */
  private void signed(ExecutableElement method, AnnotationMirror action, TypeElement subject) {
    Optional<ExecutableElement> found = signature((TypeElement) action.getAnnotationType().asElement())
        .flatMap(ActionProcessor::method)
        .filter(ActionProcessor::shaped);
    if (found.isEmpty()) {
      // A malformed action is reported where it is declared
      return;
    }
    ExecutableElement shape = found.get();
    String name = name(method);
    String written = written(action);
    TypeMirror takes = shape.getParameters().getFirst().asType();
    TypeMirror context = shape.getParameters().getLast().asType();
    TypeElement declaring = (TypeElement) method.getEnclosingElement();
    boolean statical = method.getModifiers().contains(Modifier.STATIC);
    // An action acting on types has a signature taking only its context
    boolean typed = shape.getParameters().size() == 1;
    boolean named = mirror(method, Subject.class).isPresent();
    List<? extends VariableElement> parameters = method.getParameters();
    if (typed && !statical) {
      error(method, "%s contributes to %s, which acts on types, so it is static and takes only its %s.", name,
          written, name(context));
    } else if (named && !statical) {
      error(method, "%s is an instance method, so it acts on the type declaring it and names no @Subject.", name);
    } else if (named && !typed) {
      error(method, "%s is static, so it acts on the type of its first parameter and names no @Subject.", name);
    } else if (!typed && statical
        && (parameters.size() != 2 || !types.isSameType(parameters.getLast().asType(), context))) {
      error(method, "%s is static, so it takes the %s it acts on, then its %s, and nothing else.", name,
          name(takes), name(context));
    } else if (!typed && !types.isAssignable(types.erasure(subject.asType()), types.erasure(takes))) {
      error(method, "%s acts on %s, but %s acts only on %s.", name, subject.getQualifiedName(), written, name(takes));
    } else if (!exposed(declaring) || !exposed(subject)) {
      error(method, "%s is a contribution, so %s are public: the registry calls it.", name, declaring.equals(subject)
          ? String.format("%s and every type enclosing it", declaring.getQualifiedName())
          : String.format("%s, %s, and every type enclosing them", declaring.getQualifiedName(),
              subject.getQualifiedName()));
    } else if ((typed || !statical)
        && (parameters.size() != 1 || !types.isSameType(parameters.getFirst().asType(), context))) {
      error(method, "%s takes its %s, and nothing else.", name, name(context));
    } else if (shape.getReturnType().getKind() == TypeKind.VOID
        ? method.getReturnType().getKind() != TypeKind.VOID
        : !types.isAssignable(method.getReturnType(), shape.getReturnType())) {
      error(method, "%s returns %s, as %s does.", name, name(shape.getReturnType()), written);
    } else {
      method.getThrownTypes().stream()
          .filter(thrown -> !assignable(thrown, RuntimeException.class) && !assignable(thrown, Error.class))
          .filter(thrown -> shape.getThrownTypes().stream().noneMatch(allowed -> types.isAssignable(thrown, allowed)))
          .findFirst()
          .ifPresent(thrown -> error(method, "%s throws %s, which %s does not.", name, name(thrown), written));
    }
  }

  /**
   * Whether a signature's method has an action's shape: the subject, then the
   * context, or the context alone for an action acting on types.
   */
  private static boolean shaped(ExecutableElement shape) {
    return shape.getParameters().size() == 1 || shape.getParameters().size() == 2;
  }

  /**
   * Returns the one abstract method of a functional interface.
   */
  private static Optional<ExecutableElement> method(TypeElement signature) {
    if (signature.getKind() != ElementKind.INTERFACE) {
      return Optional.empty();
    }
    List<ExecutableElement> abstracted = ElementFilter.methodsIn(signature.getEnclosedElements()).stream()
        .filter(method -> method.getModifiers().contains(Modifier.ABSTRACT))
        .toList();
    return abstracted.size() == 1 ? Optional.of(abstracted.getFirst()) : Optional.empty();
  }

  /**
   * Names an action as written, such as {@code @Install}.
   */
  private static String written(AnnotationMirror action) {
    return "@%s".formatted(action.getAnnotationType().asElement().getSimpleName());
  }

  /**
   * Whether the package declaring a type is exported to every module, as it
   * is on the class path, so a public lookup reaches the type's public
   * members.
   */
  private boolean exported(TypeElement type) {
    ModuleElement module = elements.getModuleOf(type);
    PackageElement declaring = elements.getPackageOf(type);
    return module.isUnnamed() || ElementFilter.exportsIn(module.getDirectives()).stream()
        .anyMatch(exports -> exports.getPackage().equals(declaring) && exports.getTargetModules() == null);
  }

  /**
   * Whether an element declares the targets it applies to itself.
   */
  private static boolean attributed(Element element) {
    return element.getAnnotationsByType(Applies.class).length > 0;
  }

  /**
   * One action, as written, contributed to one subject by one key: what a
   * coverage checks together.
   *
   * @param action  the action, such as {@code @Install}
   * @param subject the type it acts on
   * @param key     the key it is contributed by, or empty for none
   */
  private record Covered(String action, TypeElement subject, Optional<String> key) {
  }
}
