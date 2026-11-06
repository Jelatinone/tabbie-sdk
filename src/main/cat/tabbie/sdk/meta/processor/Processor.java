package cat.tabbie.sdk.meta.processor;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import cat.tabbie.sdk.meta.action.Action;
import cat.tabbie.sdk.meta.action.Closure;
import cat.tabbie.sdk.meta.action.Contributor;
import cat.tabbie.sdk.meta.attribute.Conditions;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.attribute.Environments;
import cat.tabbie.sdk.meta.attribute.Platforms;
import cat.tabbie.sdk.meta.attribute.Versions;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/**
 * What every processor of tabbie's annotations shares: the annotations it
 * reads, the source versions it supports, the compiler's utilities,
 * reporting errors, and reading elements into the records the registry reads
 * at run time. Each subject's processor extends it and holds only its own
 * rules, run once each round, and once more when processing is over.
 *
 * <p>
 * Reading a method here builds the same {@link Contributor} and
 * {@link Conditions} the registry builds from the same annotations when it
 * loads a module, through the same factories, so what a module's compiler
 * checks is what the registry chooses from.
 */
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract sealed class Processor extends AbstractProcessor
    permits ActionProcessor, RegistrationProcessor {

  /**
   * Canonical names of the annotations the processor reads, or {@code *} for
   * every annotation.
   */
  Set<String> reads;

  /**
   * The compiler's utilities for elements, set when the compiler starts the
   * processor.
   */
  @NonFinal
  Elements elements;

  /**
   * The compiler's utilities for types, set when the compiler starts the
   * processor.
   */
  @NonFinal
  Types types;

  /**
   * Creates a processor reading some annotations.
   *
   * @param reads canonical names of the annotations it reads, or {@code *}
   *              for every annotation
   */
  protected Processor(@NonNull Set<String> reads) {
    this.reads = Set.copyOf(reads);
  }

  @Override
  public final Set<String> getSupportedAnnotationTypes() {
    return reads;
  }

  @Override
  public final SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public final synchronized void init(ProcessingEnvironment environment) {
    super.init(environment);
    elements = environment.getElementUtils();
    types = environment.getTypeUtils();
  }

  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
    if (round.processingOver()) {
      over();
    } else {
      round(round);
    }
    return false;
  }

  /**
   * Applies the processor's rules to what one round found.
   *
   * @param round the round
   */
  protected abstract void round(@NonNull RoundEnvironment round);

  /**
   * Applies the rules that can only run once processing is over; none by
   * default.
   */
  protected void over() {
  }

  /**
   * Reads a method contributing an action, as the registry reads it at run
   * time: the type it acts on, each attribute from its nearest declaration,
   * where it acts, and its key.
   *
   * @param method method carrying the action
   * @param action the action, as the method carries it
   * @return the contributor, holding the method's element
   * @throws IllegalArgumentException when an attribute is malformed, or the
   *                                  action does not declare where it acts
   */
  protected Contributor<TypeElement, ExecutableElement> contributor(
      @NonNull ExecutableElement method,
      @NonNull AnnotationMirror action) {
    Conditions conditions = Conditions.of(
        nearest(method, Versions.class).stream().findFirst(),
        nearest(method, Distributions.class).stream().findFirst(),
        nearest(method, Environments.class).stream().findFirst(),
        nearest(method, Platforms.class));
    return new Contributor<>(name(method), subject(method, action), conditions, closure(action), key(action),
        method);
  }

  /**
   * Returns the value a contribution is keyed by: that of the element its
   * action's {@link Action#key() key} names, written as the registry writes
   * it, a class by its binary name.
   *
   * @param action the action, as a method carries it
   * @return the key, or empty when the action is keyed by nothing
   */
  protected Optional<String> key(@NonNull AnnotationMirror action) {
    return keyName((TypeElement) action.getAnnotationType().asElement())
        .flatMap(name -> element(action, name))
        .map(AnnotationValue::getValue)
        .map(value -> switch (value) {
          case TypeMirror type -> elements.getBinaryName((TypeElement) types.asElement(type)).toString();
          case VariableElement constant -> constant.getSimpleName().toString();
          default -> String.valueOf(value);
        });
  }

  /**
   * Returns the name of the element an action is keyed by, as its
   * {@link Action} names it.
   *
   * @param action an action's annotation type
   * @return the element's name, or empty when the action is keyed by nothing
   */
  protected Optional<String> keyName(@NonNull TypeElement action) {
    return mirror(action, Action.class)
        .flatMap(marked -> element(marked, "key"))
        .map(AnnotationValue::getValue)
        .map(String.class::cast)
        .filter(name -> !name.isEmpty());
  }

  /**
   * Returns where a contribution acts: its action's {@code value} element, a
   * {@link Closure}, as written or by default, or the root for an action that
   * declares none.
   *
   * @param action the action, as a method carries it
   * @return where it acts
   * @throws IllegalArgumentException when the action's value is not a
   *                                  closure
   */
  protected Closure closure(@NonNull AnnotationMirror action) {
    Optional<AnnotationValue> value = element(action, "value");
    if (value.isEmpty()) {
      return Closure.ROOT;
    }
    return value
        .map(AnnotationValue::getValue)
        .filter(VariableElement.class::isInstance)
        .map(constant -> ((VariableElement) constant).getSimpleName().toString())
        .filter(name -> Stream.of(Closure.values()).anyMatch(closure -> closure.name().equals(name)))
        .map(Closure::valueOf)
        .orElseThrow(() -> new IllegalArgumentException(String.format("@%s is an action, so its value() is a "
            + "Closure, where its contributions act.", action.getAnnotationType().asElement().getSimpleName())));
  }

  /**
   * Returns the type an action acts on: the type its {@code subject} element
   * names, or else the type declaring the method. A {@code Class} element is
   * the one value the compiler cannot hand over as an annotation instance, so
   * it is read from the mirror.
   *
   * @param method method carrying the action
   * @param action the action, as the method carries it
   * @return the type it acts on
   */
  protected TypeElement subject(@NonNull ExecutableElement method, @NonNull AnnotationMirror action) {
    return element(action, "subject")
        .map(AnnotationValue::getValue)
        .stream()
        .flatMap(value -> value instanceof List<?> values
            ? values.stream().map(held -> ((AnnotationValue) held).getValue())
            : Stream.of(value))
        .filter(TypeMirror.class::isInstance)
        .map(type -> (TypeElement) types.asElement((TypeMirror) type))
        .findFirst()
        .orElse((TypeElement) method.getEnclosingElement());
  }

  /**
   * Returns an action's signature: the functional interface its
   * {@link Action} names.
   *
   * @param action an action's annotation type
   * @return the signature, or empty when the type is not an action
   */
  protected Optional<TypeElement> signature(@NonNull TypeElement action) {
    return mirror(action, Action.class)
        .flatMap(marked -> element(marked, "value"))
        .map(AnnotationValue::getValue)
        .filter(TypeMirror.class::isInstance)
        .map(type -> (TypeElement) types.asElement((TypeMirror) type));
  }

  /**
   * Returns the value of an annotation's element, as written or by default.
   *
   * @param annotation an annotation, as an element carries it
   * @param name       the element's name
   * @return its value, or empty when the annotation declares no such element
   */
  protected Optional<AnnotationValue> element(@NonNull AnnotationMirror annotation, @NonNull String name) {
    return elements.getElementValuesWithDefaults(annotation).entrySet().stream()
        .filter(element -> element.getKey().getSimpleName().contentEquals(name))
        .map(element -> (AnnotationValue) element.getValue())
        .findFirst();
  }

  /**
   * Returns the actions a method carries: its annotations whose own
   * annotation type is marked as an action.
   *
   * @param method a method
   * @return its actions, empty when it carries none
   */
  protected static List<? extends AnnotationMirror> actions(@NonNull ExecutableElement method) {
    return method.getAnnotationMirrors().stream()
        .filter(mirror -> mirror.getAnnotationType().asElement().getAnnotation(Action.class) != null)
        .toList();
  }

  /**
   * Returns the mirror of an annotation an element carries.
   *
   * @param element    an element
   * @param annotation the annotation's type
   * @return its mirror, or empty when the element does not carry it
   */
  protected static Optional<AnnotationMirror> mirror(
      @NonNull Element element,
      @NonNull Class<? extends Annotation> annotation) {
    return element.getAnnotationMirrors().stream()
        .filter(mirror -> ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName()
            .contentEquals(annotation.getCanonicalName()))
        .map(AnnotationMirror.class::cast)
        .findFirst();
  }

  /**
   * Lists the types among some elements, and every type nested in them.
   *
   * @param elements elements, such as a round's root elements
   * @return the types, each before those nested in it
   */
  protected static Stream<TypeElement> nested(@NonNull Collection<? extends Element> elements) {
    return ElementFilter.typesIn(elements).stream()
        .flatMap(type -> Stream.concat(Stream.of(type), nested(type.getEnclosedElements())));
  }

  /**
   * Returns the nearest declaration of an attribute: on the element itself,
   * or else on the nearest type enclosing it.
   *
   * @param <Attribute> the attribute's type
   * @param element     a method or type
   * @param attribute   the attribute's type
   * @return each declaration of the attribute where it is nearest, empty
   *         when none is
   */
  protected static <Attribute extends Annotation> List<Attribute> nearest(
      @NonNull Element element,
      @NonNull Class<Attribute> attribute) {
    return Stream.iterate(element,
        enclosing -> enclosing instanceof ExecutableElement || enclosing instanceof TypeElement,
        Element::getEnclosingElement)
        .map(enclosing -> List.of(enclosing.getAnnotationsByType(attribute)))
        .filter(found -> !found.isEmpty())
        .findFirst()
        .orElse(List.of());
  }

  /**
   * Whether a type, and every type enclosing it, is public, so code in
   * another package can name it.
   *
   * @param type a type
   * @return whether it can be named from anywhere in its module
   */
  protected static boolean exposed(@NonNull TypeElement type) {
    return Stream.iterate((Element) type, TypeElement.class::isInstance, Element::getEnclosingElement)
        .allMatch(enclosing -> enclosing.getModifiers().contains(Modifier.PUBLIC));
  }

  /**
   * Whether a type is a kind of a class, ignoring type arguments.
   *
   * @param type a type
   * @param kind a class
   * @return whether the type's erasure is assignable to the class's
   */
  protected boolean assignable(@NonNull TypeMirror type, @NonNull Class<?> kind) {
    return types.isAssignable(types.erasure(type), types.erasure(element(kind).asType()));
  }

  /**
   * Whether a type is a class, ignoring type annotations such as
   * {@code @NonNull}.
   *
   * @param type a type
   * @param kind a class without type parameters
   * @return whether they are the same type
   */
  protected boolean same(@NonNull TypeMirror type, @NonNull Class<?> kind) {
    return types.isSameType(type, element(kind).asType());
  }

  /**
   * Returns the compiler's element for a class.
   *
   * @param kind a class
   * @return its element
   */
  protected TypeElement element(@NonNull Class<?> kind) {
    return elements.getTypeElement(kind.getCanonicalName());
  }

  /**
   * Reports an error on an element, failing the compilation.
   *
   * @param element   the element the error is about
   * @param format    message format, as {@link String#format} takes it
   * @param arguments message arguments
   */
  protected void error(@NonNull Element element, @NonNull String format, @NonNull Object... arguments) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(format, arguments), element);
  }

  /**
   * Names a method as written: the type declaring it, its name, and the
   * types of its parameters, as the registry names it at run time.
   *
   * @param method a method
   * @return its name, such as {@code Mod.installRoot(Install.Context)}
   */
  protected static String name(@NonNull ExecutableElement method) {
    return String.format("%s.%s(%s)", name((TypeElement) method.getEnclosingElement()), method.getSimpleName(),
        method.getParameters().stream()
            .map(parameter -> name(parameter.asType()))
            .collect(Collectors.joining(", ")));
  }

  /**
   * Names a type as written, with its type arguments and without the type
   * annotations, such as {@code @NonNull}, that the compiler's own text for
   * a type includes.
   *
   * @param type a type
   * @return its name, such as {@code Set<World>}
   */
  protected static String name(@NonNull TypeMirror type) {
    return switch (type) {
      case DeclaredType declared when declared.getTypeArguments().isEmpty() ->
          name((TypeElement) declared.asElement());
      case DeclaredType declared -> name((TypeElement) declared.asElement()) + declared.getTypeArguments().stream()
          .map(Processor::name)
          .collect(Collectors.joining(", ", "<", ">"));
      case WildcardType wildcard when wildcard.getExtendsBound() != null ->
          "? extends " + name(wildcard.getExtendsBound());
      case WildcardType wildcard when wildcard.getSuperBound() != null -> "? super " + name(wildcard.getSuperBound());
      case WildcardType _ -> "?";
      case ArrayType array -> name(array.getComponentType()) + "[]";
      case PrimitiveType primitive -> primitive.getKind().name().toLowerCase(Locale.ROOT);
      default -> type.toString();
    };
  }

  /**
   * Names a class by its simple name within the classes enclosing it.
   */
  private static String name(TypeElement type) {
    return String.join(".", Stream.iterate((Element) type, TypeElement.class::isInstance,
        Element::getEnclosingElement)
        .map(enclosing -> enclosing.getSimpleName().toString())
        .toList()
        .reversed());
  }
}
