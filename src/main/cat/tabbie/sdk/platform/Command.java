package cat.tabbie.sdk.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.NonNull;

/**
 * A declarative program invocation: an executable followed by ordered tokens.
 * Describing a command runs nothing. Executors render it into an argument
 * vector, binding scoped paths and toolchain programs to their physical
 * locations, and run it without a shell, so no token is ever reinterpreted.
 *
 * @param executable program to run
 * @param tokens     ordered arguments and options
 */
public record Command(@NonNull Argument executable, @NonNull List<Token> tokens) {

	/**
	 * Copies the tokens and rejects a blank literal program.
	 *
	 * @throws IllegalArgumentException when the executable is a blank literal
	 */
	public Command {
		tokens = List.copyOf(tokens);
		if (executable instanceof Argument.Literal(String value) && value.isBlank()) {
			throw new IllegalArgumentException("A command needs a program to run.");
		}
	}

	/**
	 * Joins fixed arguments, caller options, and trailing arguments.
	 *
	 * @param program  program name
	 * @param leading  fixed leading arguments
	 * @param options  caller options
	 * @param trailing fixed trailing arguments
	 * @return literal command
	 */
	public static Command of(String program, List<String> leading, List<String> options, List<String> trailing) {
		List<String> arguments = new ArrayList<>(leading);
		arguments.addAll(options);
		arguments.addAll(trailing);
		return of(program, arguments);
	}

	/**
	 * Describes a program found by name, such as on the executor's search path,
	 * with literal arguments.
	 *
	 * @param executable program name or path
	 * @param arguments  literal arguments
	 * @return literal command
	 * @throws IllegalArgumentException when the program name is blank
	 */
	public static Command of(@NonNull String executable, @NonNull String... arguments) {
		return of(executable, List.of(arguments));
	}

	/**
	 * Describes a program found by name with literal arguments.
	 *
	 * @param executable program name or path
	 * @param arguments  literal arguments
	 * @return literal command
	 * @throws IllegalArgumentException when the program name is blank
	 */
	public static Command of(@NonNull String executable, @NonNull List<String> arguments) {
		return new Command(new Argument.Literal(executable), Argument.literals(arguments));
	}

	/**
	 * Returns this command with further tokens appended.
	 *
	 * @param more tokens to append
	 * @return extended command
	 */
	public Command with(@NonNull List<? extends Token> more) {
		List<Token> joined = new ArrayList<>(tokens);
		joined.addAll(more);
		return new Command(executable, joined);
	}

	/**
	 * Renders the argument vector, executable first.
	 *
	 * @param resolver executor bindings for scoped paths and toolchains
	 * @return immutable argument vector
	 */
	public List<String> render(@NonNull Resolver resolver) {
		List<String> line = new ArrayList<>();
		line.add(executable.render(resolver));
		tokens.forEach(token -> line.addAll(token.expand(resolver)));
		return List.copyOf(line);
	}

	/**
	 * Executor bindings used while rendering.
	 */
	public interface Resolver {

		/**
		 * Binds a scoped logical path to a physical path.
		 *
		 * @param relative context- or world-scoped path
		 * @return physical path
		 */
		@NonNull
		Path path(@NonNull Relative relative);

		/**
		 * Locates the program of a provisioned toolchain, such as a {@code java}
		 * executable satisfying the requirement.
		 *
		 * @param toolchain required toolchain
		 * @return physical program path
		 */
		@NonNull
		Path toolchain(@NonNull Toolchain toolchain);
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
		 * an executable literal must name a program.
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
		 * A scoped logical path, bound by the executor.
		 *
		 * @param path context- or world-scoped path
		 */
		record Location(@NonNull Relative path) implements Argument {

			@Override
			public String render(@NonNull Resolver resolver) {
				return resolver.path(path).toString();
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
	}

	/**
	 * A named option with a value, rendered in one of the common styles.
	 *
	 * @param name  option name including its prefix, such as {@code --port} or
	 *              {@code -Xmx}
	 * @param value option value
	 * @param style how name and value are joined
	 */
	public record Option(@NonNull String name, @NonNull Argument value, @NonNull Style style) implements Token {

		/**
		 * Rejects a blank name.
		 *
		 * @throws IllegalArgumentException when the name is blank
		 */
		public Option {
			if (name.isBlank()) {
				throw new IllegalArgumentException("An option needs a name");
			}
		}

		/**
		 * How an option's name and value are rendered.
		 */
		public enum Style {

			/**
			 * Two arguments, such as {@code --port 25565}.
			 */
			SEPARATE,

			/**
			 * One argument joined by {@code =}, such as {@code --port=25565} or
			 * {@code -Dkey=value}.
			 */
			EQUALS,

			/**
			 * One argument with no separator, such as {@code -Xmx4G}.
			 */
			JOINED
		}

		@Override
		public List<String> expand(@NonNull Resolver resolver) {
			String rendered = value.render(resolver);
			return switch (style) {
				case SEPARATE -> List.of(name, rendered);
				case EQUALS -> List.of(name + "=" + rendered);
				case JOINED -> List.of(name + rendered);
			};
		}
	}
}