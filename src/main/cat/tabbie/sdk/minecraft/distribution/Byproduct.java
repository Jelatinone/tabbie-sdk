package cat.tabbie.sdk.minecraft.distribution;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Relative;
import cat.tabbie.sdk.platform.Toolchain;
import lombok.NonNull;

/**
 * One file of an exact distribution release, such as a server jar or a loader
 * profile. {@link #install} describes the images it places; an
 * {@link Executable} byproduct also describes how an executor starts and stops
 * it, while a {@link Nonexecutable} one is only placed. Describing a byproduct
 * never downloads runtimes, runs installers, or starts processes; carrying out
 * these descriptions belongs to Core.
 *
 * <p>
 * Implementations are records whose compact constructors copy their
 * collections and call {@link #validate(String, Set)}.
 */
public sealed interface Byproduct extends Release.Payload<Installer.Context> {

	/**
	 * Stable byproduct identity derived from the file coordinates, so equal
	 * coordinates always yield the same identity across processes.
	 *
	 * @return byproduct identity
	 */
	default Identity<Byproduct> byproductId() {
		return Identity.create(coordinates().canonical());
	}

	/**
	 * Human-readable canonical byproduct name.
	 *
	 * @return byproduct name
	 */
	@NonNull
	String byproductName();

	/**
	 * Provider-owned description of this byproduct's primary content.
	 *
	 * @return content source
	 */
	@NonNull
	Describe source();

	/**
	 * Returns the targets this byproduct supports as a whole.
	 *
	 * @return immutable, nonempty supported targets
	 */
	@NonNull
	Set<Label> labels();

	/**
	 * Returns the machine platforms this byproduct runs on, such as a native
	 * server built for one operating system.
	 *
	 * @return immutable platforms, empty for every platform
	 */
	@NonNull
	default Set<Platform> platforms() {
		return Set.of();
	}

	/**
	 * Checks whether this byproduct supports a target on a platform.
	 *
	 * @param target   selected target
	 * @param platform target machine platform
	 * @return whether a label matches the target and the platform is accepted
	 */
	default boolean supports(@NonNull Label target, @NonNull Platform platform) {
		return labels().stream().anyMatch(label -> label.match(target))
				&& (platforms().isEmpty() || platforms().contains(platform));
	}

	/**
	 * Checks declarations before a byproduct is published.
	 *
	 * @param byproductName candidate display name
	 * @param labels        candidate targets
	 * @throws IllegalArgumentException when the name is blank, there are no
	 *                                  labels, or labels span distributions
	 */
	static void validate(@NonNull String byproductName, @NonNull Set<Label> labels) {
		if (byproductName.isBlank() || labels.isEmpty()) {
			throw new IllegalArgumentException("A byproduct needs a non-blank name and targets.");
		}
		if (labels.stream().map(Label::distribution).distinct().count() != 1) {
			throw new IllegalArgumentException("A byproduct serves exactly one distribution.");
		}
	}

	/**
	 * A byproduct an executor runs, such as a server jar or a native server
	 * binary. Its start and stop arrangements are descriptions; nothing runs
	 * until an executor carries them out.
	 */
	non-sealed interface Executable extends Byproduct {

		/**
		 * Returns the toolchain the started program needs, which the executor
		 * provisions and binds through {@link Command.Argument.Binary}.
		 *
		 * @return required toolchain, or empty for a native program
		 */
		@NonNull
		default Optional<Toolchain> toolchain() {
			return Optional.empty();
		}

		/**
		 * Describes how to start this byproduct in a context, without starting it.
		 * Scoped paths in the arrangement are resolved by the executor through the
		 * context's mounts.
		 *
		 * @param context selected target and mounts
		 * @return start arrangement
		 */
		@NonNull
		Allocate allocate(@NonNull Context context);

		/**
		 * Describes how to stop this byproduct in a context, without stopping it.
		 * The arrangement must suit the one {@link #allocate} returns for the same
		 * context; see {@link Deallocate} for valid pairings.
		 *
		 * @param context selected target and mounts
		 * @return stop arrangement
		 */
		@NonNull
		Deallocate deallocate(@NonNull Context context);

		/**
		 * A declarative start arrangement an executor carries out.
		 */
		sealed interface Allocate {

			/**
			 * Returns the command the executor runs to start the byproduct.
			 *
			 * @return start command
			 */
			@NonNull
			Command command();

			/**
			 * A process the executor starts and owns until it exits, so it can write to
			 * its input, signal it, and observe its exit.
			 *
			 * @param command     start command
			 * @param pwd         scoped working directory of the process
			 * @param environment additional environment variables
			 */
			record Managed(
					@NonNull Command command,
					@NonNull Relative pwd,
					@NonNull Map<String, String> environment) implements Allocate {

				/**
				 * Copies the environment.
				 */
				public Managed {
					environment = Map.copyOf(environment);
				}
			}

			/**
			 * A command that hands the byproduct to something else, such as a service
			 * manager or an external launcher, and then exits. The executor does not
			 * own the resulting process, so it can only be stopped with
			 * {@link Deallocate.Invoke}.
			 *
			 * @param command start command, run in the context mount
			 */
			record Unmanaged(@NonNull Command command) implements Allocate {
			}
		}

		/**
		 * A declarative stop arrangement an executor carries out. {@link Input} and
		 * {@link Signal} need a process the executor owns, so they pair only with
		 * {@link Allocate.Managed Managed}; {@link Invoke} pairs with either. Executors
		 * reject other pairings.
		 */
		sealed interface Deallocate {

			/**
			 * Returns how long the executor waits for the byproduct to stop. A managed
			 * process still running afterwards is forcibly destroyed; an unmanaged stop
			 * command still running afterwards fails the stop.
			 *
			 * @return grace period
			 */
			@NonNull
			Duration grace();

			/**
			 * Writes a line to a managed process's standard input, such as
			 * {@code stop} for a Minecraft server.
			 *
			 * @param line  line to write, without a line terminator
			 * @param grace time allowed for the process to exit
			 */
			record Input(@NonNull String line, @NonNull Duration grace) implements Deallocate {
			}

			/**
			 * Asks a managed process to terminate gracefully, as with {@code SIGTERM}.
			 *
			 * @param grace time allowed for the process to exit
			 */
			record Signal(@NonNull Duration grace) implements Deallocate {
			}

			/**
			 * Runs a separate stop command, such as a service manager's stop action.
			 * For a managed byproduct the executor then waits for its process to exit;
			 * for an unmanaged one, for the stop command to exit.
			 *
			 * @param command stop command, run in the context mount
			 * @param grace   time allowed for the byproduct to stop
			 */
			record Invoke(@NonNull Command command, @NonNull Duration grace) implements Deallocate {
			}
		}
	}

	/**
	 * A byproduct that is placed but never run itself, such as a loader library
	 * or a launcher profile that another program reads.
	 */
	non-sealed interface Nonexecutable extends Byproduct {
	}

	/**
	 * An installation context with the runtime settings an executable byproduct
	 * is started with.
	 */
	interface Context extends Installer.Context {

		/**
		 * Returns the memory bound the started process is given, interpreted by
		 * the byproduct's {@link Executable#allocate(Context) allocation}, such as
		 * a JVM's maximum heap.
		 *
		 * @return maximum memory in bytes, or empty for the program's default
		 */
		@NonNull
		Optional<Long> maximumMemory();

		/**
		 * Returns caller-supplied runtime options the byproduct may apply when it
		 * describes its start arrangement.
		 *
		 * @return immutable runtime options, possibly empty
		 */
		@NonNull
		Map<String, String> runtimeOptions();

		/**
		 * Immutable context with explicit mounts and runtime settings.
		 *
		 * @param label          selected target
		 * @param platform       target platform
		 * @param repository     caller-owned retention backend
		 * @param contextRoot    context mount relative to the installation
		 * @param worldRoot      selected world relative to the context mount
		 * @param maximumMemory  positive memory bound in bytes, or empty
		 * @param runtimeOptions runtime options
		 */
		record Default(
				@NonNull Label label,
				@NonNull Platform platform,
				@NonNull Extract repository,
				@NonNull Relative.Root contextRoot,
				@NonNull Relative.World worldRoot,
				@NonNull Optional<Long> maximumMemory,
				@NonNull Map<String, String> runtimeOptions) implements Context {

			/**
			 * Copies the runtime options and checks the memory bound.
			 *
			 * @throws IllegalArgumentException when the memory bound is not positive
			 */
			public Default {
				runtimeOptions = Map.copyOf(runtimeOptions);
				if (maximumMemory.filter(bytes -> bytes < 1L).isPresent()) {
					throw new IllegalArgumentException("A memory bound must be positive.");
				}
			}

			/**
			 * Creates a context with the program's default memory and no options.
			 *
			 * @param label       selected target
			 * @param platform    target platform
			 * @param repository  caller-owned retention backend
			 * @param contextRoot context mount relative to the installation
			 * @param worldRoot   selected world relative to the context mount
			 */
			public Default(@NonNull Label label, @NonNull Platform platform, @NonNull Extract repository,
					@NonNull Relative.Root contextRoot, @NonNull Relative.World worldRoot) {
				this(label, platform, repository, contextRoot, worldRoot, Optional.empty(), Map.of());
			}
		}
	}
}