package cat.tabbie.sdk.meta.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.StandardLocation;

import cat.tabbie.sdk.content.Artifact;
import cat.tabbie.sdk.distribution.Distribution;
import cat.tabbie.sdk.meta.attribute.Distributions;
import cat.tabbie.sdk.meta.registry.Registered;
import cat.tabbie.sdk.meta.registry.Registration;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/**
 * Generates a module's {@link Registration}: a class named
 * {@code Registrations} listing the types the module registers and every type
 * declaring a contribution, in the package named after the module, or for a
 * module on the class path, in the package of its first listed type. It also
 * writes the file {@link java.util.ServiceLoader} reads on the class path. A
 * named module provides the registration itself, and the processor fails the
 * build, naming the line, when its {@code module-info} does not.
 *
 * <p>
 * A registered type is public, as is every type enclosing it, and is an enum
 * of distributions, an abstract type of them naming its group, or a type of
 * {@link Artifact}; group identifiers are unique within the module. Every
 * type is listed together in the first round with anything to list, so a type
 * another processor generates cannot be registered.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class RegistrationProcessor extends Processor {

  /**
   * Simple name of the class generated in each module.
   */
  static String GENERATED = "Registrations";

  /**
   * Where the SDK's processors find the SDK's version, written into it when
   * the SDK is built.
   */
  static String VERSION = "/META-INF/tabbie/sdk.version";

  /**
   * Where the SDK's processors find the template of each module's
   * registration: Java source with {@code {{package}}}, {@code {{version}}}
   * and {@code {{types}}} in place of the module's package, the SDK's
   * version, and the class literals listed. Its class is named
   * {@link #GENERATED}.
   */
  static String TEMPLATE = "/META-INF/tabbie/Registrations.java";

  /**
   * Registered abstract types of distributions, by the group each names.
   */
  Map<String, TypeElement> groups = new HashMap<>();

  /**
   * Whether the module's registration is generated.
   */
  @NonFinal
  boolean generated;

  /**
   * Name of the named module whose registration was generated, checked for
   * providing it once processing is over; empty for a module on the class
   * path, or before the registration is generated.
   */
  @NonFinal
  String provider = "";

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
    if (generated) {
      listed.forEach(type -> error(type, "%s is listed after this module's %s were generated; a registered type "
          + "or a contribution cannot be generated.", type.getQualifiedName(), GENERATED));
      return;
    }
    registered.forEach(this::check);
    generate(listed);
    generated = true;
  }

  @Override
  protected void over() {
    if (provider.isEmpty()) {
      return;
    }
    // Only once processing is over does the module's provides line name a class that exists
    ModuleElement module = elements.getModuleElement(provider);
    String service = Registration.class.getCanonicalName();
    String name = provider + "." + GENERATED;
    if (ElementFilter.providesIn(module.getDirectives()).stream()
        .filter(provides -> provides.getService().getQualifiedName().contentEquals(service))
        .flatMap(provides -> provides.getImplementations().stream())
        .noneMatch(implementation -> implementation.getQualifiedName().contentEquals(name))) {
      error(module, "Module %s registers contributions, so its module-info must declare: provides %s with %s;",
          provider, service, name);
    }
  }

  /**
   * Checks a registered type is what it may be.
   */
  private void check(TypeElement type) {
    String group = type.getAnnotation(Registered.class).value();
    boolean distribution = assignable(type.asType(), Distribution.class)
        && !type.getQualifiedName().contentEquals(Distribution.class.getCanonicalName());
    boolean abstracted = type.getKind() == ElementKind.INTERFACE
        || type.getKind() == ElementKind.CLASS && type.getModifiers().contains(Modifier.ABSTRACT);
    if (!exposed(type)) {
      error(type, "%s is registered, so it and every type enclosing it must be public: the generated %s name it.",
          type.getQualifiedName(), GENERATED);
    } else if (distribution && type.getKind() == ElementKind.ENUM) {
      if (!group.isEmpty()) {
        error(type, "%s registers each of its distributions, so it names no group; register an abstract type of "
            + "distributions as the group %s instead.", type.getQualifiedName(), group);
      }
    } else if (distribution && abstracted) {
      if (!Distributions.Meta.IDENTIFIER.matcher(group).matches()) {
        error(type, "%s is registered as a group of distributions, so it names the group's identifier, such as "
            + "java:launcher, rather than \"%s\".", type.getQualifiedName(), group);
      } else if (groups.putIfAbsent(group, type) != null) {
        error(type, "%s and %s are both registered as the group %s.", groups.get(group).getQualifiedName(),
            type.getQualifiedName(), group);
      }
    } else if (assignable(type.asType(), Artifact.class)
        && !type.getQualifiedName().contentEquals(Artifact.class.getCanonicalName())) {
      if (!group.isEmpty()) {
        error(type, "%s is a content family, so it names no group.", type.getQualifiedName());
      }
    } else {
      error(type, "%s is registered, but is neither an enum or abstract type of distributions nor a content "
          + "family, a type of Artifact.", type.getQualifiedName());
    }
  }

  /**
   * Writes the module's registration, and the file the service loader reads
   * on the class path.
   */
  private void generate(List<TypeElement> listed) {
    Element first = listed.getFirst();
    ModuleElement module = elements.getModuleOf(first);
    String pack = module.isUnnamed()
        ? elements.getPackageOf(first).getQualifiedName().toString()
        : module.getQualifiedName().toString();
    String name = pack + "." + GENERATED;
    provider = module.isUnnamed() ? "" : module.getQualifiedName().toString();
    Element[] originating = listed.toArray(Element[]::new);
    String types = listed.stream()
        .map(type -> String.format("\n        %s.class", type.getQualifiedName()))
        .collect(Collectors.joining(","));
    try {
      String source = read(TEMPLATE)
          .replace("{{package}}", pack)
          .replace("{{version}}", version())
          .replace("{{types}}", types);
      try (Writer writer = processingEnv.getFiler().createSourceFile(name, originating).openWriter()) {
        writer.write(source);
      }
      try (Writer writer = processingEnv.getFiler()
          .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/"
              + Registration.class.getCanonicalName(), originating)
          .openWriter()) {
        writer.write(name + "\n");
      }
    } catch (IOException exception) {
      error(first, "Could not generate %s: %s", name, exception.getMessage());
    }
  }

  /**
   * Reads the version of the SDK these processors belong to.
   */
  private static String version() throws IOException {
    String version = read(VERSION).strip();
    if (version.isEmpty()) {
      throw new IOException(String.format("The SDK's version in %s is empty.", VERSION));
    }
    return version;
  }

  /**
   * Reads one of the SDK's resources as text.
   */
  private static String read(String resource) throws IOException {
    try (InputStream stream = RegistrationProcessor.class.getResourceAsStream(resource)) {
      if (stream == null) {
        throw new IOException(String.format("The SDK's %s is missing.", resource));
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
