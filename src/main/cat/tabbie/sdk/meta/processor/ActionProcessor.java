package cat.tabbie.sdk.meta.processor;

import java.lang.annotation.Annotation;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import cat.tabbie.sdk.meta.action.Action;
import cat.tabbie.sdk.meta.action.Closure;
import cat.tabbie.sdk.meta.action.Contribution;
import cat.tabbie.sdk.meta.action.Coverage;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.attribute.Environments;
import cat.tabbie.sdk.meta.attribute.Platforms;
import cat.tabbie.sdk.meta.attribute.Versions;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

/**
 * Checks every action, and every contribution to one, a module compiles.
 *
 * <p>
 * An action declares the type a static contribution acts on,
 * {@code Class<?>[] subject()}, and, when its contributions may act in
 * different places, where each acts, {@code Closure value()}; without one,
 * every contribution acts in the root. Its {@link Action} names its signature:
 * a functional interface whose one method takes the subject, then the
 * action's context.
 *
 * <p>
 * Each method carrying an action is public and has its action's signature:
 * an instance method takes the context and acts on the type declaring it,
 * naming no subject; a static one takes the type it acts on, then the
 * context. Either returns what the signature returns, and throws nothing more.
 * The type it acts on is one the signature takes, and it and the type
 * declaring the method are public, as is every type enclosing them, so the
 * registry reaches the method through the module's lookup. The contributions
 * of each action to each subject within a type are checked together as a
 * {@link Coverage}, each conflict reported on the later of its two methods.
 * Attributes on a method carrying no action, or on a type with no action
 * within it, are an error, since nothing would read them.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ActionProcessor extends Processor {

  /**
   * Every attribute, as a method or type may carry it.
   */
  static List<Class<? extends Annotation>> ATTRIBUTES = List.of(Versions.class, Distributions.class,
      Environments.class, Platforms.class);

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
    }
    if (attributed(type) && nested(List.of(type))
        .flatMap(inner -> ElementFilter.methodsIn(inner.getEnclosedElements()).stream())
        .allMatch(method -> actions(method).isEmpty())) {
      error(type, "%s has attributes but no action within it; attributes only say which targets an action "
          + "applies to.", type.getSimpleName());
    }
    Map<Covered, List<Contribution<TypeElement, ExecutableElement>>> coverages = new LinkedHashMap<>();
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      List<? extends AnnotationMirror> actions = actions(method);
      if (actions.isEmpty()) {
        if (attributed(method)) {
          error(method, "%s has attributes but no action; attributes only say which targets an action applies "
              + "to.", name(method));
        }
        continue;
      }
      if (!method.getModifiers().contains(Modifier.PUBLIC)) {
        error(method, "%s is a contribution, so it is public: the registry calls it.", name(method));
        continue;
      }
      for (AnnotationMirror action : actions) {
        try {
          Contribution<TypeElement, ExecutableElement> contribution = contribution(method, action);
          signed(method, action, contribution.subject());
          coverages.computeIfAbsent(new Covered(written(action), contribution.subject()), _ -> new ArrayList<>())
              .add(contribution);
        } catch (IllegalArgumentException exception) {
          error(method, "%s", exception.getMessage());
        }
      }
    }
    coverages.forEach((covered, contributions) -> new Coverage<>(covered.action(), contributions).conflicts()
        .forEach(conflict -> error(conflict.later().call(), "%s", conflict.message())));
  }

  /**
   * Checks an action declares what every action does.
   */
  private void shape(TypeElement action) {
    Map<String, TypeMirror> elements = ElementFilter.methodsIn(action.getEnclosedElements()).stream()
        .collect(Collectors.toMap(element -> element.getSimpleName().toString(), ExecutableElement::getReturnType));
    // An action acting only in the root declares no closure
    if (elements.containsKey("value") && !same(elements.get("value"), Closure.class)) {
      error(action, "@%s is an action, so its value() is a Closure, where its contributions act; an action acting "
          + "only in the root declares none.", action.getSimpleName());
    }
    if (!(elements.get("subject") instanceof ArrayType array && assignable(array.getComponentType(), Class.class))) {
      error(action, "@%s is an action, so it declares Class<?>[] subject() default {}, the type a static "
          + "contribution acts on.", action.getSimpleName());
    }
    if (signature(action).flatMap(ActionProcessor::method).filter(shape -> shape.getParameters().size() == 2)
        .isEmpty()) {
      error(action, "@%s is an action, so its signature is a functional interface whose one method takes the "
          + "subject it acts on, then its context.", action.getSimpleName());
    }
  }

  /**
   * Checks a contribution has its action's signature, reporting the first way
   * it does not.
   */
  private void signed(ExecutableElement method, AnnotationMirror action, TypeElement subject) {
    Optional<ExecutableElement> found = signature((TypeElement) action.getAnnotationType().asElement())
        .flatMap(ActionProcessor::method)
        .filter(shape -> shape.getParameters().size() == 2);
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
    List<? extends VariableElement> parameters = method.getParameters();
    if (!statical && action.getElementValues().keySet().stream()
        .anyMatch(element -> element.getSimpleName().contentEquals("subject"))) {
      error(method, "%s is an instance method, so it acts on the type declaring it and names no subject.", name);
    } else if (!types.isAssignable(types.erasure(subject.asType()), types.erasure(takes))) {
      error(method, "%s acts on %s, but %s acts only on %s.", name, subject.getQualifiedName(), written, name(takes));
    } else if (!exposed(declaring) || !exposed(subject)) {
      error(method, "%s is a contribution, so %s are public: the registry calls it.", name, declaring.equals(subject)
          ? String.format("%s and every type enclosing it", declaring.getQualifiedName())
          : String.format("%s, %s, and every type enclosing them", declaring.getQualifiedName(),
              subject.getQualifiedName()));
    } else if (!statical && (parameters.size() != 1 || !types.isSameType(parameters.getFirst().asType(), context))) {
      error(method, "%s takes its %s, and nothing else.", name, name(context));
    } else if (statical && (parameters.size() != 2
        || !types.isSameType(types.erasure(parameters.getFirst().asType()), types.erasure(subject.asType()))
        || !types.isSameType(parameters.getLast().asType(), context))) {
      error(method, "%s is static, so it takes the %s it acts on, then its %s, and nothing else.", name,
          subject.getSimpleName(), name(context));
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
    return "@" + action.getAnnotationType().asElement().getSimpleName();
  }

  /**
   * Whether an element carries any attribute itself.
   */
  private static boolean attributed(Element element) {
    return ATTRIBUTES.stream().anyMatch(attribute -> element.getAnnotationsByType(attribute).length > 0);
  }

  /**
   * One action, as written, contributed to one subject: what a coverage
   * checks together.
   *
   * @param action  the action, such as {@code @Install}
   * @param subject the type it acts on
   */
  private record Covered(String action, TypeElement subject) {
  }
}
