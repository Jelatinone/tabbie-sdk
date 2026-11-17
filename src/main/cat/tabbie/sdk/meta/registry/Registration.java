package cat.tabbie.sdk.meta.registry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import cat.tabbie.sdk.failure.Reason;
import cat.tabbie.sdk.failure.Violation;
import lombok.NonNull;

/**
 * What one module adds to tabbie: the types it registers, and every type
 * declaring a contribution. While a module compiles, its processor writes them
 * to the module's {@link #INDEX index}, after the version of the SDK it
 * compiled against, and core reads every module's index into a
 * {@link Registry}, which learns what each type is from its annotations. A
 * module declares nothing for core to find it: the index is a resource, and a
 * resource under {@code META-INF} is never encapsulated. The registry calls
 * contributions through public lookups, so a module exports every package
 * holding one.
 *
 * @param sdk   version of the SDK the module was compiled against, such as
 *              {@code 0.1.0}, so core can refuse a module built for another
 * @param types the types the module registers, and every type declaring a
 *              contribution: each enum of distributions, abstract type of
 *              them registered as a group, content family, and type with a
 *              method carrying an action, in a fixed order
 */
public record Registration(@NonNull String sdk, @NonNull List<Class<?>> types) {

  /**
   * Where a module's index lies: a resource holding the SDK's version on its
   * first line, then the binary name of each type listed, one per line.
   */
  public static final String INDEX = "META-INF/tabbie/registration";

  /**
   * Copies the types, and checks the version is given.
   *
   * @throws IllegalArgumentException when the version is blank
   */
  public Registration {
    types = List.copyOf(types);
    if (sdk.isBlank()) {
      throw new IllegalArgumentException("A registration names the SDK version its module compiled against.");
    }
  }

  /**
   * Reads a named module's index, loading each type it lists from the module.
   *
   * @param module module to read
   * @return its registration, or empty for a module without an index
   * @throws IOException when the index cannot be read, or names no version
   * @throws Violation   with {@link Reason.Registry.Unreachable} when the
   *                     index lists a type the module does not hold
   */
  public static Optional<Registration> of(@NonNull Module module) throws IOException {
    try (InputStream index = module.getResourceAsStream(INDEX)) {
      return index == null
          ? Optional.empty()
          : Optional.of(read(index, name -> Optional.ofNullable(Class.forName(module, name))));
    }
  }

  /**
   * Reads every index a class loader finds, loading each type listed through
   * the loader: those of the modules it defines, and those on its class
   * path.
   *
   * @param loader class loader to read through
   * @return registrations, in the order the loader finds their indexes
   * @throws IOException when an index cannot be read, or names no version
   * @throws Violation   with {@link Reason.Registry.Unreachable} when an
   *                     index lists a type the loader cannot load
   */
  public static List<Registration> of(@NonNull ClassLoader loader) throws IOException {
    List<Registration> registrations = new ArrayList<>();
    for (URL found : Collections.list(loader.getResources(INDEX))) {
      try (InputStream index = found.openStream()) {
        registrations.add(read(index, name -> load(name, loader)));
      }
    }
    return registrations;
  }

  /**
   * Reads one index, finding each type it lists by its binary name.
   */
  private static Registration read(InputStream index, Function<String, Optional<Class<?>>> finding)
      throws IOException {
    List<String> lines = new String(index.readAllBytes(), StandardCharsets.UTF_8).lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .toList();
    if (lines.isEmpty()) {
      throw new IOException(String.format("A registration index, %s, begins with the SDK's version.", INDEX));
    }
    return new Registration(lines.getFirst(), lines.subList(1, lines.size()).stream()
        .<Class<?>>map(name -> finding.apply(name)
            .orElseThrow(() -> new Violation(new Reason.Registry.Unreachable(name))))
        .toList());
  }

  /**
   * Loads a type through a class loader, without initializing it.
   */
  private static Optional<Class<?>> load(String name, ClassLoader loader) {
    try {
      return Optional.of(Class.forName(name, false, loader));
    } catch (ClassNotFoundException exception) {
      return Optional.empty();
    }
  }
}
