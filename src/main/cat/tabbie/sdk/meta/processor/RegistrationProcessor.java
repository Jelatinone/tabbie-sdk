package cat.tabbie.sdk.meta.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.StandardLocation;

import cat.tabbie.sdk.meta.attribute.Applies;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.meta.registry.Registrable;
import cat.tabbie.sdk.meta.registry.Registration;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/**
 * Writes a module's {@link Registration} index, at
 * {@link Registration#INDEX}: the SDK's version, then the binary name of every
 * type the module registers and every type declaring a contribution, one per
 * line. Core reads it from each module it loads, so the module declares
 * nothing for it.
 *
 * <p>
 * A registered type is a type of a {@link Registrable registrable kind}: of a
 * kind registrable {@link Registrable.As#VALUES as values}, an enum of them or
 * an abstract type naming its group; of a kind registrable
 * {@link Registrable.As#TYPES as types}, any type of it. Group identifiers are
 * unique within the module. Every type is listed together in the first round
 * with anything to list, so a type another processor generates cannot be
 * registered.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class RegistrationProcessor extends Processor {

  /**
   * Where the SDK's processors find the SDK's version, written into it when
   * the SDK is built.
   */
  static String VERSION = "/META-INF/tabbie/sdk.version";

  /**
   * Registered abstract types of distributions, by the group each names.
   */
  Map<String, TypeElement> groups = new HashMap<>();

  /**
   * Whether the module's index is written.
   */
  @NonFinal
  boolean written;

  /**
   * Creates the processor, as javac does when it finds it on the processor
   * path. A type declaring a contribution may carry no registration, so it
   * reads every annotation.
   */
  public RegistrationProcessor() {
    super(Set.of("*"));
  }

  @Override
  protected void round(RoundEnvironment round) {
    Set<TypeElement> registered = ElementFilter.typesIn(round.getElementsAnnotatedWith(Registered.class));
    List<TypeElement> listed = Stream.concat(registered.stream(), nested(round.getRootElements())
        .filter(type -> ElementFilter.methodsIn(type.getEnclosedElements()).stream()
            .anyMatch(method -> !actions(method).isEmpty())))
        .distinct()
        .sorted(Comparator.comparing(type -> type.getQualifiedName().toString()))
        .toList();
    if (listed.isEmpty()) {
      return;
    }
    if (written) {
      listed.forEach(type -> error(type, """
          %s is listed after this module's index was written; a registered type or a contribution cannot be \
          generated.""", type.getQualifiedName()));
      return;
    }
    registered.forEach(this::check);
    write(listed);
    written = true;
  }

  /**
   * Checks a registered type is what its registrable kind lets it be.
   */
  private void check(TypeElement type) {
    String group = type.getAnnotation(Registered.class).value();
    Optional<Registrable.As> registrable = registrable(type);
    if (mirror(type, Registrable.class).isPresent()) {
      error(type, "%s is a registrable kind, so its own types are registered rather than it.",
          type.getQualifiedName());
      return;
    }
    if (registrable.isEmpty()) {
      error(type, "%s is registered, but is a type of no registrable kind, a type marked @Registrable.",
          type.getQualifiedName());
      return;
    }
    switch (registrable.get()) {
      case TYPES -> {
        if (!group.isEmpty()) {
          error(type, "%s registers itself, so it names no group.", type.getQualifiedName());
        }
      }
      case VALUES -> {
        boolean abstracted = type.getKind() == ElementKind.INTERFACE
            || type.getKind() == ElementKind.CLASS && type.getModifiers().contains(Modifier.ABSTRACT);
        if (type.getKind() == ElementKind.ENUM) {
          if (!group.isEmpty()) {
            error(type, """
                %s registers each of its values, so it names no group; register an abstract type of them as the \
                group %s instead.""", type.getQualifiedName(), group);
          }
        } else if (!abstracted) {
          error(type, """
              %s is registered as a type of a kind registrable as values, so it is an enum of them or an abstract \
              type grouping them.""", type.getQualifiedName());
        } else if (!Applies.IDENTIFIER.matcher(group).matches()) {
          error(type, """
              %s is registered as a group, so it names the group's identifier, such as java:launcher, rather than \
              "%s".""", type.getQualifiedName(), group);
        } else if (groups.putIfAbsent(group, type) != null) {
          error(type, "%s and %s are both registered as the group %s.", groups.get(group).getQualifiedName(),
              type.getQualifiedName(), group);
        }
      }
    }
  }

  /**
   * Finds how a type registers: as the registrable kind it is a type of says,
   * the nearest of its supertypes marked {@link Registrable}.
   */
  private Optional<Registrable.As> registrable(TypeElement type) {
    Deque<TypeMirror> pending = new ArrayDeque<>(types.directSupertypes(type.asType()));
    Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      TypeMirror next = pending.removeFirst();
      if (!(types.asElement(next) instanceof TypeElement element)
          || !seen.add(element.getQualifiedName().toString())) {
        continue;
      }
      Registrable registrable = element.getAnnotation(Registrable.class);
      if (registrable != null) {
        return Optional.of(registrable.value());
      }
      pending.addAll(types.directSupertypes(next));
    }
    return Optional.empty();
  }

  /**
   * Writes the module's index: the SDK's version, then each listed type's
   * binary name.
   */
  private void write(List<TypeElement> listed) {
    Element[] originating = listed.toArray(Element[]::new);
    try (Writer writer = processingEnv.getFiler()
        .createResource(StandardLocation.CLASS_OUTPUT, "", Registration.INDEX, originating)
        .openWriter()) {
      writer.write(Stream.concat(Stream.of(version()), listed.stream()
          .map(type -> elements.getBinaryName(type).toString()))
          .collect(Collectors.joining("\n", "", "\n")));
    } catch (IOException exception) {
      error(listed.getFirst(), "Could not write %s: %s", Registration.INDEX, exception.getMessage());
    }
  }

  /**
   * Reads the version of the SDK these processors belong to.
   */
  private static String version() throws IOException {
    try (InputStream stream = RegistrationProcessor.class.getResourceAsStream(VERSION)) {
      if (stream == null) {
        throw new IOException(String.format("The SDK's %s is missing.", VERSION));
      }
      String version = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
      if (version.isEmpty()) {
        throw new IOException(String.format("The SDK's version in %s is empty.", VERSION));
      }
      return version;
    }
  }
}
