package cat.tabbie.sdk.minecraft.distribution;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * One file of an exact distribution release, such as a server jar or a loader
 * profile. {@link #install} describes the images it places, and
 * {@link #allocate} and {@link #deallocate} describe how an executor starts and
 * stops it in a context. Describing an instance never downloads runtimes, runs
 * installers, or starts processes; carrying out these descriptions belongs to
 * Core.
 */
public sealed interface Byproduct extends Release.Payload<Installer.Context> {

	/**
	 * Stable instance identity derived from the file coordinates, so equal
	 * coordinates always yield the same identity across processes.
	 *
	 * @return instance identity
	 */
	default Identity<Byproduct> byproductId() {
		return Identity.create(coordinates().canonical());
	}

	/**
	 * Human-readable canonical instance name.
	 *
	 * @return instance name
	 */
	@NonNull
	String byproductName();

	/**
	 * Provider-owned description of this instance's primary content.
	 *
	 * @return content source
	 */
	@NonNull
	Describe source();

	/**
	 * Returns the targets this instance supports as a whole.
	 *
	 * @return immutable, nonempty supported targets
	 */
	@NonNull
	Set<Label> labels();

	non-sealed interface Executable extends Byproduct {

		/**
		 * Describes how to start this instance in a context, without starting it.
		 * Scoped paths in the arrangement are resolved by the executor through the
		 * context's mounts.
		 *
		 * @param context selected target and mounts
		 * @return start arrangement
		 */
		@NonNull
		Allocate allocate(@NonNull Installer.Context context);

		/**
		 * Describes how to stop this instance in a context, without stopping it. The
		 * arrangement must suit the one {@link #allocate} returns for the same
		 * context; see {@link Deallocate} for valid pairings.
		 *
		 * @param context selected target and mounts
		 * @return stop arrangement
		 */
		@NonNull
		Deallocate deallocate(@NonNull Installer.Context context);

		/**
		 * A declarative start arrangement an executor carries out.
		 */
		sealed interface Allocate {

			/**
			 * Returns the command the executor runs to start the instance.
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
			 * A command that hands the instance to something else, such as a service
			 * manager or an external launcher, and then exits. The executor does not
			 * own the resulting process, so the instance can only be stopped with
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
			 * Returns how long the executor waits for the instance to stop. A managed
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
			 * For a managed instance the executor then waits for its process to exit;
			 * for an unmanaged one, for the stop command to exit.
			 *
			 * @param command stop command, run in the context mount
			 * @param grace   time allowed for the instance to stop
			 */
			record Invoke(@NonNull Command command, @NonNull Duration grace) implements Deallocate {
			}
		}
	}

	non-sealed interface Nonexecutable extends Byproduct {

		// Is there any implementation needed here?
	}
}