package cat.tabbie.sdk.distribution;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.catalog.Identity;
import cat.tabbie.sdk.catalog.Release;
import cat.tabbie.sdk.content.Installer;
import cat.tabbie.sdk.model.Layout;
import cat.tabbie.sdk.model.Violation;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Toolchain;
import cat.tabbie.sdk.revision.Image;
import cat.tabbie.sdk.target.Label;
import cat.tabbie.sdk.target.Platform;
import lombok.NonNull;

/**
 * One file of an exact distribution release, such as a server jar or a loader
 * profile. {@link #install} captures its source and describes the images that
 * place it; an {@link Executable} distributable also describes how an executor
 * starts and stops it, while a {@link Nonexecutable} one is only placed.
 * Installing a distributable captures only its own content: it never downloads
 * runtimes, runs other installers, or starts processes; carrying out its
 * descriptions belongs to Core.
 *
 * <p>
 * Implementations are records whose compact constructors copy their
 * collections and call {@link #validate(String, Set)}.
 */
public sealed interface Distributable extends Release.Payload, Installer<Installer.Context> {

  /**
   * Stable distributable identity derived from the file coordinates, so equal
   * coordinates always yield the same identity across processes.
   *
   * @return distributable identity
   */
  default Identity<Distributable> distributableId() {
    return Identity.create(coordinates().canonical());
  }

  /**
   * Human-readable canonical distributable name.
   *
   * @return distributable name
   */
  @NonNull
  String distributableName();

  /**
   * Provider-owned description of this distributable's primary content.
   *
   * @return content source
   */
  @NonNull
  Describe source();

  /**
   * Returns the targets this distributable supports as a whole.
   *
   * @return immutable, nonempty supported targets
   */
  @NonNull
  Set<Label> labels();

  /**
   * Returns the machine platforms this distributable runs on, such as a native
   * server built for one operating system.
   *
   * @return immutable platforms, empty for every platform
   */
  @NonNull
  default Set<Platform> platforms() {
    return Set.of();
  }

  /**
   * Checks whether this distributable supports a target on a platform.
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
   * Places the source as one file, named after it, in the directory of the
   * context's model that accepts this distributable's type, where
   * {@link #location} finds it in the Cat's model. Whoever installs chooses
   * the distributable that {@link #supports} the target while resolving;
   * installing does not ask again.
   *
   * @param context selected target and the plate it is installed on
   * @return validated immutable images
   * @throws IOException when the content cannot be captured
   * @throws Violation   when the tree does not allow the file
   */
  @Override
  default Set<Image<?>> install(@NonNull Installer.Context context) throws IOException {
    return new Installer.Install(context, getClass()).file(source());
  }

  /**
   * Locates this distributable's file in the model of the Cat it runs in:
   * beneath the declared directory that accepts its type, named after its
   * source, as its default install places it. Only declarations accept
   * content, so the model alone decides it, whatever is on disk.
   *
   * @param model model of a Cat, declared from its root
   * @return address of the file beneath the Cat's root
   * @throws Violation.Unplaced  when no directory accepts the type
   * @throws Violation.Ambiguous when several directories accept the type
   */
  @NonNull
  default Address location(@NonNull Layout.Directory model) {
    return model.find(getClass()).unique().address().resolve(source().fileName());
  }

  /**
   * Checks declarations before a distributable is published.
   *
   * @param distributableName candidate display name
   * @param labels        candidate targets
   * @throws IllegalArgumentException when the name is blank, there are no
   *                                  labels, or labels span distributions
   */
  static void validate(@NonNull String distributableName, @NonNull Set<Label> labels) {
    if (distributableName.isBlank() || labels.isEmpty()) {
      throw new IllegalArgumentException("A distributable needs a non-blank name and targets.");
    }
    if (labels.stream().map(Label::distribution).distinct().count() != 1) {
      throw new IllegalArgumentException("A distributable serves exactly one distribution.");
    }
  }

  /**
   * A distributable an executor runs, such as a server jar or a native server
   * binary. Its start and stop arrangements are descriptions; nothing runs
   * until an executor carries them out.
   */
  non-sealed interface Executable extends Distributable {

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
     * Describes how to start this distributable in a context, without starting
     * it. Addresses in the arrangement are bound to physical paths by the
     * executor.
     *
     * @param context selected target, the Cat's model and runtime settings
     * @return start arrangement
     */
    @NonNull
    Allocate allocate(@NonNull Context context);

    /**
     * Describes how to stop this distributable in a context, without stopping
     * it. The arrangement must suit the one {@link #allocate} returns for the
     * same context; see {@link Deallocate} for valid pairings.
     *
     * @param context selected target, the Cat's model and runtime settings
     * @return stop arrangement
     */
    @NonNull
    Deallocate deallocate(@NonNull Context context);

    /**
     * A declarative start arrangement an executor carries out.
     */
    sealed interface Allocate {

      /**
       * Returns the command the executor runs to start the distributable.
       *
       * @return start command
       */
      @NonNull
      Command command();

      /**
       * A process the executor starts and owns until it exits, so it can write
       * to its input, signal it, and observe its exit.
       *
       * @param command     start command
       * @param pwd         working directory of the process
       * @param environment additional environment variables
       */
      record Managed(
          @NonNull Command command,
          @NonNull Address pwd,
          @NonNull Map<String, String> environment) implements Allocate {

        /**
         * Copies the environment.
         */
        public Managed {
          environment = Map.copyOf(environment);
        }
      }

      /**
       * A command that hands the distributable to something else, such as a
       * service manager or an external launcher, and then exits. The executor
       * does not own the resulting process, so it can only be stopped with
       * {@link Deallocate.Invoke}.
       *
       * @param command start command, run in the root the executor binds
       */
      record Unmanaged(@NonNull Command command) implements Allocate {
      }
    }

    /**
     * A declarative stop arrangement an executor carries out. {@link Input} and
     * {@link Signal} need a process the executor owns, so they pair only with
     * {@link Allocate.Managed Managed}; {@link Invoke} pairs with either.
     * Executors reject other pairings.
     */
    sealed interface Deallocate {

      /**
       * Returns how long the executor waits for the distributable to stop. A
       * managed process still running afterwards is forcibly destroyed; an
       * unmanaged stop command still running afterwards fails the stop.
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
       * Asks a managed process to terminate gracefully, as with {@code
       * SIGTERM}.
       *
       * @param grace time allowed for the process to exit
       */
      record Signal(@NonNull Duration grace) implements Deallocate {
      }

      /**
       * Runs a separate stop command, such as a service manager's stop action.
       * For a managed distributable the executor then waits for its process to
       * exit; for an unmanaged one, for the stop command to exit.
       *
       * @param command stop command, run in the root the executor binds
       * @param grace   time allowed for the distributable to stop
       */
      record Invoke(@NonNull Command command, @NonNull Duration grace) implements Deallocate {
      }
    }
  }

  /**
   * A distributable that is placed but never run itself, such as a loader
   * library or a launcher profile that another program reads.
   */
  non-sealed interface Nonexecutable extends Distributable {
  }

  /**
   * The target an executable distributable is started for, the model of the Cat
   * it runs in, and the runtime settings it is started with. Describing a
   * start reads no file, so the context is a value and holds no plate: the
   * executor binds the arrangement's addresses to paths.
   */
  interface Context {

    /**
     * Returns the explicitly selected target.
     *
     * @return selected target
     */
    @NonNull
    Label label();

    /**
     * Returns the platform of the machine the distributable runs on, for
     * selecting a native command.
     *
     * @return target platform
     */
    @NonNull
    Platform platform();

    /**
     * Returns the model of the Cat the distributable runs in, as its release
     * declares it, so a start arrangement can {@link Distributable#location
     * locate} what the release's installs placed without reading the Cat.
     *
     * @return model declared from the root
     */
    @NonNull
    Layout.Directory model();

    /**
     * Returns the memory bound the started process is given, interpreted by the
     * distributable's {@link Executable#allocate(Context) allocation}, such as
     * a JVM's maximum heap.
     *
     * @return maximum memory in bytes, or empty for the program's default
     */
    @NonNull
    Optional<Long> maximumMemory();

    /**
     * Returns caller-supplied runtime options the distributable may apply when
     * it describes its start arrangement.
     *
     * @return immutable runtime options, possibly empty
     */
    @NonNull
    Map<String, String> runtimeOptions();

    /**
     * Immutable context with runtime settings.
     *
     * @param label          selected target
     * @param platform       target platform
     * @param model          model of the Cat the distributable runs in,
     *                       declared from the root
     * @param maximumMemory  positive memory bound in bytes, or empty
     * @param runtimeOptions runtime options
     */
    record Default(
        @NonNull Label label,
        @NonNull Platform platform,
        @NonNull Layout.Directory model,
        @NonNull Optional<Long> maximumMemory,
        @NonNull Map<String, String> runtimeOptions) implements Context {

      /**
       * Copies the runtime options, then checks the model and the memory
       * bound.
       *
       * @throws IllegalArgumentException when the model is not declared from
       *                                  the root, or the memory bound is not
       *                                  positive
       */
      public Default {
        runtimeOptions = Map.copyOf(runtimeOptions);
        if (!model.address().isRoot()) {
          throw new IllegalArgumentException("A model is declared from the root.");
        }
        if (maximumMemory.filter(bytes -> bytes < 1L).isPresent()) {
          throw new IllegalArgumentException("A memory bound must be positive.");
        }
      }

      /**
       * Creates a context with the program's default memory and no options.
       *
       * @param label    selected target
       * @param platform target platform
       * @param model    model of the Cat the distributable runs in, declared
       *                 from the root
       */
      public Default(@NonNull Label label, @NonNull Platform platform, @NonNull Layout.Directory model) {
        this(label, platform, model, Optional.empty(), Map.of());
      }
    }
  }
}
