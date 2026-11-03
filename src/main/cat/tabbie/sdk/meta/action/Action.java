package cat.tabbie.sdk.meta.action;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import cat.tabbie.sdk.meta.attribute.Closures;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.attribute.Environments;
import cat.tabbie.sdk.meta.attribute.Platforms;
import cat.tabbie.sdk.meta.attribute.Versions;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import cat.tabbie.sdk.platform.Platform;
import lombok.NonNull;

/**
 * Marks an annotation as an action: something a module contributes that the
 * SDK chooses by target, placed on the method that provides it. Most actions
 * are verbs whose method carries a behaviour out, such as installing a
 * family's content; some are nouns whose method returns what they name, such
 * as the toolchain a server needs. Attributes on the method, or on a type
 * enclosing it, say which targets it applies to; a method with none applies to
 * every target.
 *
 * <p>
 * Every action has the same shape, so a new action is only an annotation
 * marked with this one:
 * <ul>
 * <li>Its contributions are read into {@link Meta} records and checked
 * together as a {@link Coverage}: none is private, no two apply to one
 * target, and for each target, every version between the lowest and highest
 * bound they name is covered. Versions outside those bounds are simply not
 * supported.</li>
 * <li>An action acting on a type of content names it with a {@code Class}
 * element called {@code value}, such as an install naming the family it
 * installs, and is checked together with every other contribution to that
 * type in the module. Without one, it acts on the type declaring its
 * method.</li>
 * <li>An action acting in a place says so with {@link #scoped()}, and each of
 * its contributions then says where with a
 * {@link cat.tabbie.sdk.meta.attribute.Closures Closures} attribute.</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Action {

  /**
   * Returns whether the action acts in a place, as installing content does.
   * Each contribution of such an action says where with a
   * {@link Closures Closures} attribute, on its method or on a type enclosing
   * it; an action that does not act in a place refuses one on its method.
   *
   * @return whether contributions are scoped
   */
  boolean scoped() default false;

  /**
   * One method contributing an action, and what its attributes say about the
   * targets it applies to and where it acts.
   *
   * @param contributor   method contributing the action, as written, such as
   *                      {@code install()}
   * @param scope         where it acts, or empty for an action that does not
   *                      act in a place
   * @param versions      versions it applies to
   * @param distributions distributions it applies to
   * @param environments  environments it applies to
   * @param platforms     platforms it applies to
   */
  record Meta(
      @NonNull String contributor,
      @NonNull Optional<Closures.Closure> scope,
      @NonNull Versions.Meta versions,
      @NonNull Distributions.Meta distributions,
      @NonNull Environments.Meta environments,
      @NonNull Platforms.Meta platforms) {

    /**
     * Checks every part is given.
     */
    public Meta {
    }
  }

  /**
   * The contributions of one action to one subject, checked together. A
   * target is one distribution, environment and platform, and for every
   * target, no two contributions apply, and every version between the lowest
   * and highest bound of those that do is covered. The targets checked are
   * each value some contribution names, and for each kind of value, one more
   * standing for every value none names.
   *
   * @param action        the action as written, such as {@code @Install}
   * @param contributions its contributions, in the order they are declared
   */
  record Coverage(@NonNull String action, @NonNull List<Meta> contributions) {

    /**
     * Copies the contributions.
     */
    public Coverage {
      contributions = List.copyOf(contributions);
    }

    /**
     * Finds every conflict between the contributions, each pair once, at the
     * first target it is found at.
     *
     * @return conflicts, empty when the contributions agree
     */
    public List<Conflict> conflicts() {
      List<Conflict> conflicts = new ArrayList<>();
      Set<List<Object>> reported = new HashSet<>();
      for (Spot spot : spots()) {
        List<Meta> applying = contributions.stream()
            .filter(spot::reaches)
            .sorted(Comparator.comparing((Meta contribution) -> contribution.versions().low().orElse(null),
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
        if (applying.isEmpty()) {
          continue;
        }
        // The contribution reaching furthest so far; each next one meets it, leaves a gap after it, or overlaps it
        Meta reach = applying.getFirst();
        for (Meta next : applying.subList(1, applying.size())) {
          Optional<Version.Number> ends = reach.versions().high();
          Optional<Version.Number> begins = next.versions().low();
          if (ends.isEmpty() || begins.isEmpty() || begins.get().compareTo(ends.get()) < 0) {
            if (reported.add(List.of(Conflict.Overlap.class, reach, next))) {
              conflicts.add(new Conflict.Overlap(action, reach, next, spot.toString()));
            }
          } else if (begins.get().compareTo(ends.get()) > 0 && reported.add(List.of(Conflict.Gap.class, reach, next))) {
            conflicts.add(new Conflict.Gap(action, reach, next, spot.toString()));
          }
          Optional<Version.Number> reaches = next.versions().high();
          if (ends.isPresent() && (reaches.isEmpty() || reaches.get().compareTo(ends.get()) > 0)) {
            reach = next;
          }
        }
      }
      return List.copyOf(conflicts);
    }

    /**
     * Lists every target to check, in a fixed order.
     */
    private List<Spot> spots() {
      Set<Optional<String>> distributions = values(contribution -> contribution.distributions().ids(),
          Comparator.<String>naturalOrder());
      Set<Optional<Environment>> environments = values(contribution -> contribution.environments().environments(),
          Comparator.<Environment>naturalOrder());
      Set<Optional<Platform>> platforms = values(contribution -> contribution.platforms().platforms(),
          Comparator.comparing(Platform::kernel).thenComparing(Platform::architecture));
      return distributions.stream()
          .flatMap(distribution -> environments.stream()
              .flatMap(environment -> platforms.stream()
                  .map(platform -> new Spot(distribution, environment, platform))))
          .toList();
    }

    /**
     * Lists the values of one kind the contributions name, in order, then one
     * standing for every value none names.
     */
    private <Value> Set<Optional<Value>> values(Function<Meta, Set<Value>> named, Comparator<Value> order) {
      return Stream.concat(
          contributions.stream()
              .flatMap(contribution -> named.apply(contribution).stream())
              .sorted(order)
              .map(Optional::of),
          Stream.of(Optional.<Value>empty()))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * One target: a distribution, an environment and a platform, each empty
     * for every value no contribution names.
     *
     * @param distribution distribution identifier
     * @param environment  environment
     * @param platform     platform
     */
    private record Spot(
        Optional<String> distribution,
        Optional<Environment> environment,
        Optional<Platform> platform) {

      /**
       * Whether a contribution applies to this target.
       */
      boolean reaches(Meta contribution) {
        return contribution.distributions().includes(distribution)
            && contribution.environments().includes(environment)
            && contribution.platforms().includes(platform);
      }

      /**
       * Names the target by the values it has.
       */
      @Override
      public String toString() {
        String named = Stream.of(distribution, environment.map(Enum::name),
            platform.map(machine -> machine.kernel() + "/" + machine.architecture()))
            .flatMap(Optional::stream)
            .collect(Collectors.joining(", "));
        return named.isEmpty() ? "every target" : named;
      }
    }
  }

  /**
   * Two contributions of one action that disagree about a target, reported
   * on the later of the two.
   */
  sealed interface Conflict permits Conflict.Overlap, Conflict.Gap {

    /**
     * Returns the later of the two contributions, where the conflict is
     * reported.
     *
     * @return later contribution
     */
    @NonNull
    Meta later();

    /**
     * Describes the conflict as a complete sentence.
     *
     * @return message
     */
    @NonNull
    String message();

    /**
     * Two contributions that both apply to a target.
     *
     * @param action  the action as written
     * @param earlier contribution whose versions begin first
     * @param later   contribution whose versions begin within the earlier's
     * @param target  the target both apply to
     */
    record Overlap(
        @NonNull String action,
        @NonNull Meta earlier,
        @NonNull Meta later,
        @NonNull String target) implements Conflict {

      /**
       * Checks every part is given.
       */
      public Overlap {
      }

      @Override
      public String message() {
        return String.format("Two %s contributions, %s and %s, both apply to %s; no two may apply to one target.",
            action, earlier.contributor(), later.contributor(), target);
      }
    }

    /**
     * Two contributions to a target with versions between them that neither
     * applies to.
     *
     * @param action  the action as written
     * @param earlier contribution whose versions end before the gap
     * @param later   contribution whose versions begin after the gap
     * @param target  the target the gap is in
     */
    record Gap(
        @NonNull String action,
        @NonNull Meta earlier,
        @NonNull Meta later,
        @NonNull String target) implements Conflict {

      /**
       * Checks every part is given, and that there are versions between the
       * two.
       *
       * @throws IllegalArgumentException when the earlier has no high bound,
       *                                  the later has no low bound, or the
       *                                  later's begins no later than the
       *                                  earlier's ends
       */
      public Gap {
        if (earlier.versions().high().isEmpty() || later.versions().low().isEmpty()
            || later.versions().low().get().compareTo(earlier.versions().high().get()) <= 0) {
          throw new IllegalArgumentException("A gap lies between where one contribution's versions end and the "
              + "next's begin.");
        }
      }

      @Override
      public String message() {
        Version.Number ends = earlier.versions().high().orElseThrow();
        Version.Number begins = later.versions().low().orElseThrow();
        return String.format("%s contributions leave the versions from %s up to %s uncovered for %s: %s ends at "
            + "%s, but %s begins at %s.", action, ends, begins, target, earlier.contributor(), ends,
            later.contributor(), begins);
      }
    }
  }

  /**
   * The annotation processor checking a module's contributions while it
   * compiles. Each method carrying an action is read into a {@link Meta}, and
   * the contributions of each action to each subject within a type are
   * checked together as a {@link Coverage}. Each attribute a {@code Meta}
   * record refuses, and each conflict found, fails the compilation on the
   * method it is about. An action cannot be private, since the code generated
   * for it calls it, and attributes on a method carrying no action, or on a
   * type with no action within it, are an error, since nothing would read
   * them.
   *
   * <p>
   * javac finds it on the processor path, where a module puts the SDK to have
   * its contributions checked; the SDK compiles itself with it too. It only
   * checks: it changes no class, generates nothing yet, and reads nothing by
   * reflection.
   */
  final class Processor extends AbstractProcessor {

    /**
     * Every attribute, as a method or type may carry it.
     */
    private static final List<Class<? extends Annotation>> ATTRIBUTES = List.of(Versions.class,
        Distributions.class, Environments.class, Platforms.class, Closures.class);

    /**
     * Creates the processor, as javac does when it finds it on the processor
     * path.
     */
    public Processor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      // An action is any annotation marked as one, so every annotation is looked at
      return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
      types(round.getRootElements()).forEach(this::check);
      return false;
    }

    /**
     * Lists the types among some elements, and every type nested in them.
     */
    private Stream<TypeElement> types(Collection<? extends Element> elements) {
      return ElementFilter.typesIn(elements).stream()
          .flatMap(type -> Stream.concat(Stream.of(type), types(type.getEnclosedElements())));
    }

    /**
     * Reads each method of a type that carries an action, and checks the
     * contributions of each action to each subject together. Attributes on
     * the type itself must apply to some action within it.
     */
    private void check(TypeElement type) {
      if (attributed(type) && types(List.of(type))
          .flatMap(nested -> ElementFilter.methodsIn(nested.getEnclosedElements()).stream())
          .allMatch(method -> actions(method).isEmpty())) {
        error(type, String.format("%s has attributes but no action within it; attributes only say which targets "
            + "an action applies to.", type.getSimpleName()));
      }
      Map<String, ExecutableElement> contributors = new HashMap<>();
      // Each action as written, with the subject it acts on, and its contributions
      Map<List<String>, List<Meta>> coverages = new LinkedHashMap<>();
      for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
        List<? extends AnnotationMirror> actions = actions(method);
        if (actions.isEmpty()) {
          if (attributed(method)) {
            error(method, String.format("%s has attributes but no action; attributes only say which targets an "
                + "action applies to.", method));
          }
          continue;
        }
        if (method.getModifiers().contains(Modifier.PRIVATE)) {
          error(method, "An action cannot be private, since the code generated for it calls it.");
          continue;
        }
        try {
          Optional<Closures.Closure> scope = nearest(method, Closures.class).stream()
              .findFirst()
              .map(Closures::value);
          Versions.Meta versions = nearest(method, Versions.class).stream()
              .findFirst()
              .map(Versions.Meta::of)
              .orElse(Versions.Meta.EVERY);
          Distributions.Meta distributions = nearest(method, Distributions.class).stream()
              .findFirst()
              .map(Distributions.Meta::of)
              .orElse(Distributions.Meta.EVERY);
          Environments.Meta environments = nearest(method, Environments.class).stream()
              .findFirst()
              .map(Environments.Meta::of)
              .orElse(Environments.Meta.EVERY);
          Platforms.Meta platforms = Platforms.Meta.of(nearest(method, Platforms.class).toArray(Platforms[]::new));
          for (AnnotationMirror action : actions) {
            String display = "@" + action.getAnnotationType().asElement().getSimpleName();
            boolean scoped = action.getAnnotationType().asElement().getAnnotation(Action.class).scoped();
            if (scoped && scope.isEmpty()) {
              error(method, String.format("%s acts in a place; give %s, or a type enclosing it, a @Closures "
                  + "attribute.", display, method));
              continue;
            }
            if (!scoped && method.getAnnotation(Closures.class) != null) {
              error(method, String.format("%s does not act in a place, so %s cannot carry @Closures.", display,
                  method));
              continue;
            }
            Meta contribution = new Meta(method.toString(), scoped ? scope : Optional.empty(), versions,
                distributions, environments, platforms);
            contributors.put(contribution.contributor(), method);
            coverages.computeIfAbsent(List.of(display, subject(action, type)), _ -> new ArrayList<>())
                .add(contribution);
          }
        } catch (IllegalArgumentException exception) {
          error(method, exception.getMessage());
        }
      }
      coverages.forEach((action, contributions) -> new Coverage(action.getFirst(), contributions).conflicts()
          .forEach(conflict -> error(contributors.get(conflict.later().contributor()), conflict.message())));
    }

    /**
     * Returns the actions a method carries: its annotations whose own
     * annotation type is marked as an action.
     */
    private List<? extends AnnotationMirror> actions(ExecutableElement method) {
      return method.getAnnotationMirrors().stream()
          .filter(mirror -> mirror.getAnnotationType().asElement().getAnnotation(Action.class) != null)
          .toList();
    }

    /**
     * Whether an element carries any attribute itself.
     */
    private boolean attributed(Element element) {
      return ATTRIBUTES.stream().anyMatch(attribute -> element.getAnnotationsByType(attribute).length > 0);
    }

    /**
     * Returns the nearest declaration of an attribute: on the element itself,
     * or else on the nearest type enclosing it.
     */
    private <Attribute extends Annotation> List<Attribute> nearest(Element element, Class<Attribute> attribute) {
      return Stream.iterate(element,
          enclosing -> enclosing instanceof ExecutableElement || enclosing instanceof TypeElement,
          Element::getEnclosingElement)
          .map(enclosing -> List.of(enclosing.getAnnotationsByType(attribute)))
          .filter(found -> !found.isEmpty())
          .findFirst()
          .orElse(List.of());
    }

    /**
     * Returns what an action acts on: the type its {@code value} element names
     * when it has one, or else the type declaring its method.
     */
    private String subject(AnnotationMirror action, TypeElement type) {
      return processingEnv.getElementUtils().getElementValuesWithDefaults(action).entrySet().stream()
          .filter(element -> element.getKey().getSimpleName().contentEquals("value"))
          .map(element -> element.getValue().getValue())
          .filter(TypeMirror.class::isInstance)
          .map(Object::toString)
          .findFirst()
          .orElse(type.getQualifiedName().toString());
    }

    /**
     * Reports an error on an element, failing the compilation.
     */
    private void error(Element element, String message) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
  }
}
