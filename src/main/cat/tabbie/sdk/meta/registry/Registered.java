package cat.tabbie.sdk.meta.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.minecraft.distribution.Distribution;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/**
 * Registers a type with tabbie: an enum of {@link Distribution distributions},
 * whose every value is registered, or a content family, a type of
 * {@link Artifact}. Registering is how a module, the SDK included, tells
 * tabbie what it adds. While the module compiles, the {@link Processor}
 * writes its registered types into one generated {@link Registration}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Registered {

  /**
   * The annotation processor generating a module's {@link Registration}: a
   * class named {@code Registrations}, in the package named after the module,
   * or for a module on the class path, in the package of its first registered
   * type. It also writes the file {@link java.util.ServiceLoader} reads on the
   * class path. A named module provides the registration itself, and the
   * processor fails the build, naming the line, when its {@code module-info}
   * does not.
   *
   * <p>
   * A registered type must be public, since the generated class names it, and
   * must be an enum of distributions or a type of {@link Artifact}. Every
   * registered type is registered together in the first round, so a type
   * another processor generates cannot be registered.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  final class Processor extends AbstractProcessor {

    /**
     * Simple name of the class generated in each module.
     */
    static String GENERATED = "Registrations";

    /**
     * Where the SDK's processor finds the SDK's version, written into it when
     * the SDK is built.
     */
    static String VERSION = "/META-INF/tabbie/sdk.version";

    /**
     * Registered enums of distributions, in order.
     */
    List<TypeElement> distributions = new ArrayList<>();

    /**
     * Registered content families, in order.
     */
    List<TypeElement> families = new ArrayList<>();

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
     * path.
     */
    public Processor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of(Registered.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
      if (round.processingOver()) {
        provided();
        return false;
      }
      List<TypeElement> found = ElementFilter.typesIn(round.getElementsAnnotatedWith(Registered.class)).stream()
          .sorted(Comparator.comparing(type -> type.getQualifiedName().toString()))
          .toList();
      if (found.isEmpty()) {
        return false;
      }
      if (generated) {
        found.forEach(type -> error(type, "%s is registered after this module's %s were generated; a registered "
            + "type cannot be generated.", type.getQualifiedName(), GENERATED));
        return false;
      }
      for (TypeElement type : found) {
        if (!type.getModifiers().contains(Modifier.PUBLIC)) {
          error(type, "%s is registered, so it must be public: the generated %s name it.", type.getQualifiedName(),
              GENERATED);
        } else if (type.getKind() == ElementKind.ENUM && assignable(type, Distribution.class)) {
          distributions.add(type);
        } else if (assignable(type, Artifact.class)
            && !type.getQualifiedName().contentEquals(Artifact.class.getCanonicalName())) {
          families.add(type);
        } else {
          error(type, "%s is registered, but is neither an enum of distributions nor a content family, a type of "
              + "Artifact.", type.getQualifiedName());
        }
      }
      generate(found.getFirst(), found);
      generated = true;
      return false;
    }

    /**
     * Writes the module's registration, and the file the service loader reads
     * on the class path.
     */
    private void generate(TypeElement first, List<TypeElement> found) {
      ModuleElement module = processingEnv.getElementUtils().getModuleOf(first);
      String pack = module.isUnnamed()
          ? processingEnv.getElementUtils().getPackageOf(first).getQualifiedName().toString()
          : module.getQualifiedName().toString();
      String name = pack + "." + GENERATED;
      String service = Registration.class.getCanonicalName();
      provider = module.isUnnamed() ? "" : module.getQualifiedName().toString();
      Element[] originating = found.toArray(Element[]::new);
      try {
        try (Writer writer = processingEnv.getFiler().createSourceFile(name, originating).openWriter()) {
          writer.write(source(pack));
        }
        try (Writer writer = processingEnv.getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + service, originating)
            .openWriter()) {
          writer.write(name + "\n");
        }
      } catch (IOException exception) {
        error(first, "Could not generate %s: %s", name, exception.getMessage());
      }
    }

    /**
     * Checks the named module whose registration was generated provides it.
     * Only once processing is over does the module's {@code provides} line
     * name a class that exists, so only then can it be read.
     */
    private void provided() {
      if (provider.isEmpty()) {
        return;
      }
      ModuleElement module = processingEnv.getElementUtils().getModuleElement(provider);
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
     * Writes the source of the module's registration.
     */
    private String source(String pack) throws IOException {
      String distribution = Distribution.class.getCanonicalName();
      String artifact = Artifact.class.getCanonicalName();
      String enums = distributions.stream()
          .map(type -> "\n        " + type.getQualifiedName() + ".values()")
          .collect(Collectors.joining(","));
      String classes = families.stream()
          .map(type -> "\n        " + type.getQualifiedName() + ".class")
          .collect(Collectors.joining(","));
      return """
          package %1$s;

          /**
           * What this module registers with tabbie, generated from its registered
           * types. The module provides it as a service, and core finds it with
           * {@link java.util.ServiceLoader}.
           */
          public final class %2$s implements %3$s {

            /**
             * Creates the registration, as the service loader does.
             */
            public %2$s() {
            }

            @Override
            public String sdk() {
              return "%4$s";
            }

            @Override
            public java.util.List<%5$s> distributions() {
              return java.util.stream.Stream.<%5$s[]>of(%6$s)
                  .flatMap(java.util.Arrays::stream)
                  .toList();
            }

            @Override
            public java.util.Set<Class<? extends %7$s>> families() {
              return java.util.Set.of(%8$s);
            }
          }
          """.formatted(pack, GENERATED, Registration.class.getCanonicalName(), version(), distribution, enums,
          artifact, classes);
    }

    /**
     * Reads the version of the SDK this processor belongs to.
     */
    private String version() throws IOException {
      try (InputStream stream = Registered.class.getResourceAsStream(VERSION)) {
        if (stream == null) {
          throw new IOException("The SDK's version is missing from " + VERSION + ".");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
          return reader.lines()
              .map(String::strip)
              .filter(line -> !line.isEmpty())
              .findFirst()
              .orElseThrow(() -> new IOException("The SDK's version in " + VERSION + " is empty."));
        }
      }
    }

    /**
     * Whether a type is a kind of another.
     */
    private boolean assignable(TypeElement type, Class<?> kind) {
      TypeElement target = processingEnv.getElementUtils().getTypeElement(kind.getCanonicalName());
      return target != null && processingEnv.getTypeUtils().isAssignable(
          processingEnv.getTypeUtils().erasure(type.asType()), processingEnv.getTypeUtils().erasure(target.asType()));
    }

    /**
     * Reports an error on an element, failing the compilation.
     */
    private void error(Element element, String format, Object... arguments) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(format, arguments), element);
    }
  }
}
