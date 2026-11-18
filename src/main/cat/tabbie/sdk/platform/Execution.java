package cat.tabbie.sdk.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cat.tabbie.sdk.tree.Address;
import lombok.NonNull;

/**
 * A declarative run of a program: the program followed by ordered tokens, run
 * in a working directory with environment variables added. Describing an
 * execution runs nothing. Executors render it into an argument vector and
 * variables, binding addresses and toolchain programs to their physical
 * locations, and run it without a shell, so no token is ever reinterpreted.
 *
 * @param program     program to run
 * @param tokens      ordered arguments and options
 * @param directory   working directory beneath the executor's root, the root
 *                    itself by default
 * @param environment environment variables added for the run, each value an
 *                    argument the executor renders
 */
public record Execution(
    @NonNull Argument program,
    @NonNull List<Token> tokens,
    @NonNull Address directory,
    @NonNull Map<String, Argument> environment) {

  /**
   * Copies the tokens and the environment, and checks the program and each
   * variable's name.
   *
   * @throws IllegalArgumentException when the program is a blank literal, or a
   *                                  variable's name is blank or holds
   *                                  {@code =}
   */
  public Execution {
    tokens = List.copyOf(tokens);
    environment = Map.copyOf(environment);
    if (program instanceof Argument.Literal(String value) && value.isBlank()) {
      throw new IllegalArgumentException("An execution needs a program to run.");
    }
    if (environment.keySet().stream().anyMatch(name -> name.isBlank() || name.indexOf('=') >= 0)) {
      throw new IllegalArgumentException("An environment variable's name is not blank and holds no '='.");
    }
  }

  /**
   * Describes a program with tokens, run at the root with no variables added.
   *
   * @param program program to run
   * @param tokens  ordered arguments and options
   * @throws IllegalArgumentException when the program is a blank literal
   */
  public Execution(@NonNull Argument program, @NonNull List<Token> tokens) {
    this(program, tokens, Address.root(), Map.of());
  }

  /**
   * Joins fixed arguments, caller options, and trailing arguments.
   *
   * @param program  program name
   * @param leading  fixed leading arguments
   * @param options  caller options
   * @param trailing fixed trailing arguments
   * @return literal execution, run at the root
   * @throws IllegalArgumentException when the program name is blank
   */
  public static Execution of(
      @NonNull String program,
      @NonNull List<String> leading,
      @NonNull List<String> options,
      @NonNull List<String> trailing) {
    List<String> arguments = new ArrayList<>(leading);
    arguments.addAll(options);
    arguments.addAll(trailing);
    return of(program, arguments);
  }

  /**
   * Describes a program found by name, such as on the executor's search path,
   * with literal arguments.
   *
   * @param program   program name or path
   * @param arguments literal arguments
   * @return literal execution, run at the root
   * @throws IllegalArgumentException when the program name is blank
   */
  public static Execution of(@NonNull String program, @NonNull String... arguments) {
    return of(program, List.of(arguments));
  }

  /**
   * Describes a program found by name with literal arguments.
   *
   * @param program   program name or path
   * @param arguments literal arguments
   * @return literal execution, run at the root
   * @throws IllegalArgumentException when the program name is blank
   */
  public static Execution of(@NonNull String program, @NonNull List<String> arguments) {
    return new Execution(new Argument.Literal(program), Argument.literals(arguments));
  }

  /**
   * Returns this execution with further tokens appended.
   *
   * @param more tokens to append
   * @return extended execution
   */
  public Execution with(@NonNull List<? extends Token> more) {
    List<Token> joined = new ArrayList<>(tokens);
    joined.addAll(more);
    return new Execution(program, joined, directory, environment);
  }

  /**
   * Returns this execution run in another working directory.
   *
   * @param directory working directory beneath the executor's root
   * @return the execution, run there
   */
  public Execution withDirectory(@NonNull Address directory) {
    return new Execution(program, tokens, directory, environment);
  }

  /**
   * Returns this execution with an environment variable added, or replaced
   * when it is already set.
   *
   * @param name  the variable's name
   * @param value the variable's value
   * @return the execution, with the variable set
   * @throws IllegalArgumentException when the name is blank or holds
   *                                  {@code =}
   */
  public Execution withEnvironment(@NonNull String name, @NonNull Argument value) {
    Map<String, Argument> variables = new HashMap<>(environment);
    variables.put(name, value);
    return new Execution(program, tokens, directory, variables);
  }

  /**
   * Renders the argument vector, program first.
   *
   * @param resolver executor bindings for addresses and toolchains
   * @return immutable argument vector
   */
  public List<String> render(@NonNull Resolver resolver) {
    List<String> line = new ArrayList<>();
    line.add(program.render(resolver));
    tokens.forEach(token -> line.addAll(token.expand(resolver)));
    return List.copyOf(line);
  }

  /**
   * Renders the environment variables added for the run.
   *
   * @param resolver executor bindings for addresses and toolchains
   * @return immutable variables, by name
   */
  public Map<String, String> environment(@NonNull Resolver resolver) {
    return environment.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, variable -> variable.getValue().render(resolver)));
  }

  /**
   * Executor bindings used while rendering, for the machine the execution
   * runs on.
   */
  public interface Resolver {

    /**
     * Binds an address to a physical path.
     *
     * @param address path beneath the executor's root
     * @return physical path
     */
    @NonNull
    Path path(@NonNull Address address);

    /**
     * Locates the program of a provisioned toolchain, such as a {@code java}
     * executable satisfying the requirement.
     *
     * @param toolchain required toolchain
     * @return physical program path
     */
    @NonNull
    Path toolchain(@NonNull Toolchain toolchain);

    /**
     * Returns what separates the entries of a path list on the machine the
     * execution runs on, such as {@code :} on Linux and macOS and {@code ;} on
     * Windows.
     *
     * @return the separator
     */
    @NonNull
    String separator();
  }

  /**
   * One or more rendered arguments.
   */
  public sealed interface Token permits Argument, Option {

    /**
     * Renders this token's arguments.
     *
     * @param resolver executor bindings
     * @return rendered arguments, in order
     */
    @NonNull
    List<String> expand(@NonNull Resolver resolver);
  }

  /**
   * A single rendered argument.
   */
  public sealed interface Argument extends Token {

    /**
     * Renders this argument.
     *
     * @param resolver executor bindings
     * @return rendered argument
     */
    @NonNull
    String render(@NonNull Resolver resolver);

    @Override
    default List<String> expand(@NonNull Resolver resolver) {
      return List.of(render(resolver));
    }

    /**
     * Wraps literal values.
     *
     * @param values literal values
     * @return immutable literal arguments
     */
    static List<Token> literals(@NonNull List<String> values) {
      return values.stream().<Token>map(Literal::new).toList();
    }

    /**
     * Wraps literal values.
     *
     * @param values literal values
     * @return immutable literal arguments
     */
    static List<Token> literals(@NonNull String... values) {
      return literals(Arrays.asList(values));
    }

    /**
     * Text passed unchanged. An empty literal is a legitimate empty argument;
     * a program literal must name one.
     *
     * @param value argument text
     */
    record Literal(@NonNull String value) implements Argument {

      @Override
      public String render(@NonNull Resolver resolver) {
        return value;
      }
    }

    /**
     * An address, bound to a physical path by the executor.
     *
     * @param address path beneath the executor's root
     */
    record Location(@NonNull Address address) implements Argument {

      @Override
      public String render(@NonNull Resolver resolver) {
        return resolver.path(address).toString();
      }
    }

    /**
     * The program of a provisioned toolchain, located by the executor, such as
     * the {@code java} launcher of a matching Java runtime.
     *
     * @param toolchain required toolchain
     */
    record Binary(@NonNull Toolchain toolchain) implements Argument {

      @Override
      public String render(@NonNull Resolver resolver) {
        return resolver.toolchain(toolchain).toString();
      }
    }

    /**
     * Arguments rendered and joined into one, such as a system property whose
     * value is a location: {@code -Djava.library.path=} followed by a
     * directory.
     *
     * @param parts the arguments joined, in order, at least one
     */
    record Concat(@NonNull List<Argument> parts) implements Argument {

      /**
       * Copies the parts, and checks there is one.
       *
       * @throws IllegalArgumentException when there are no parts
       */
      public Concat {
        parts = List.copyOf(parts);
        if (parts.isEmpty()) {
          throw new IllegalArgumentException("A joined argument has at least one part.");
        }
      }

      /**
       * Joins arguments into one.
       *
       * @param parts the arguments joined, in order, at least one
       * @return the joined argument
       * @throws IllegalArgumentException when there are no parts
       */
      public static Concat of(@NonNull Argument... parts) {
        return new Concat(List.of(parts));
      }

      @Override
      public String render(@NonNull Resolver resolver) {
        return parts.stream()
            .map(part -> part.render(resolver))
            .collect(Collectors.joining());
      }
    }

    /**
     * Arguments rendered and joined by the separator of the machine the
     * execution runs on, such as a classpath of locations.
     *
     * @param entries the entries, in order, at least one
     */
    record PathList(@NonNull List<Argument> entries) implements Argument {

      /**
       * Copies the entries, and checks there is one.
       *
       * @throws IllegalArgumentException when there are no entries
       */
      public PathList {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) {
          throw new IllegalArgumentException("A path list has at least one entry.");
        }
      }

      /**
       * Lists entries.
       *
       * @param entries the entries, in order, at least one
       * @return the path list
       * @throws IllegalArgumentException when there are no entries
       */
      public static PathList of(@NonNull Argument... entries) {
        return new PathList(List.of(entries));
      }

      @Override
      public String render(@NonNull Resolver resolver) {
        return entries.stream()
            .map(entry -> entry.render(resolver))
            .collect(Collectors.joining(resolver.separator()));
      }
    }
  }

  /**
   * A named option with a value, each kind rendering the two in its own style.
   */
  public sealed interface Option extends Token {

    /**
     * Returns the option's name, its prefix included, such as {@code --port}
     * or {@code -Xmx}.
     *
     * @return the name
     */
    @NonNull
    String name();

    /**
     * Returns the option's value.
     *
     * @return the value
     */
    @NonNull
    Argument value();

    /**
     * The name and the value as two arguments, such as
     * {@code --port 25565}.
     *
     * @param name  the option's name, its prefix included
     * @param value the option's value
     */
    record Separate(@NonNull String name, @NonNull Argument value) implements Option {

      /**
       * Checks the name.
       *
       * @throws IllegalArgumentException when the name is blank
       */
      public Separate {
        if (name.isBlank()) {
          throw new IllegalArgumentException("An option has a name.");
        }
      }

      @Override
      public List<String> expand(@NonNull Resolver resolver) {
        return List.of(name, value.render(resolver));
      }
    }

    /**
     * The name and the value joined by {@code =} into one argument, such as
     * {@code --port=25565} or {@code -Dkey=value}.
     *
     * @param name  the option's name, its prefix included
     * @param value the option's value
     */
    record Assigned(@NonNull String name, @NonNull Argument value) implements Option {

      /**
       * Checks the name.
       *
       * @throws IllegalArgumentException when the name is blank
       */
      public Assigned {
        if (name.isBlank()) {
          throw new IllegalArgumentException("An option has a name.");
        }
      }

      @Override
      public List<String> expand(@NonNull Resolver resolver) {
        return List.of(String.format("%s=%s", name, value.render(resolver)));
      }
    }

    /**
     * The name and the value with nothing between them, as one argument, such
     * as {@code -Xmx4G}.
     *
     * @param name  the option's name, its prefix included
     * @param value the option's value
     */
    record Attached(@NonNull String name, @NonNull Argument value) implements Option {

      /**
       * Checks the name.
       *
       * @throws IllegalArgumentException when the name is blank
       */
      public Attached {
        if (name.isBlank()) {
          throw new IllegalArgumentException("An option has a name.");
        }
      }

      @Override
      public List<String> expand(@NonNull Resolver resolver) {
        return List.of(name.concat(value.render(resolver)));
      }
    }
  }
}
