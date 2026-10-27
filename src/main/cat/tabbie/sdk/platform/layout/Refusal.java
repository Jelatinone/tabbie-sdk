package cat.tabbie.sdk.platform.layout;

import java.io.Serial;
import java.nio.file.FileSystemException;

import cat.tabbie.sdk.platform.Address;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Thrown when a plate will not carry out an operation, and changed nothing.
 * {@link #reason()} says why. Failures while reading or writing are other
 * {@link java.io.IOException IOExceptions}: a refusal means the plate looked,
 * and the path or its lease did not allow the operation.
 *
 * <p>
 * Callers that decide whether work passes or fails catch the refusal and
 * switch over its reason; callers that do not care let it propagate.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Refusal extends FileSystemException {

  @Serial
  private static final long serialVersionUID = 1L;

  transient Reason reason;

  /**
   * Refuses an operation.
   *
   * @param reason why the plate refused
   */
  public Refusal(@NonNull Reason reason) {
    super(reason.address().canonical(), reason.above() ? reason.at().canonical() : null, reason.message());
    this.reason = reason;
  }

  /**
   * Returns why the plate refused.
   *
   * @return refusal reason
   */
  public Reason reason() {
    return reason;
  }

  /**
   * What a path holds.
   */
  @Getter
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public enum Kind {

    /**
     * Nothing.
     */
    ABSENT("nothing"),

    /**
     * A regular file.
     */
    FILE("a file"),

    /**
     * A directory.
     */
    DIRECTORY("a directory"),

    /**
     * Something other than a file or directory, such as a dangling link.
     */
    OTHER("something other than a file or directory");

    String phrase;
  }

  /**
   * Why a plate refused. Every reason names the path the operation was on and
   * the path that decided it: the operation's own path when anything is there,
   * otherwise the nearest existing path above it.
   */
  public sealed interface Reason permits Mismatch, Denied, Held {

    /**
     * Returns the path the operation was on.
     *
     * @return operation's path
     */
    @NonNull
    Address address();

    /**
     * Returns the path that decided the refusal.
     *
     * @return the operation's own path, or a path above it
     */
    @NonNull
    Address at();

    /**
     * Describes the refusal as a sentence.
     *
     * @return refusal message
     */
    @NonNull
    String message();

    /**
     * Whether a path above the operation's path decided the refusal.
     *
     * @return true when {@link #at()} is not the operation's own path
     */
    default boolean above() {
      return !at().equals(address());
    }
  }

  /**
   * The path that decided holds something other than the operation requires
   * there. Creating requires nothing at the operation's path; reading,
   * replacing and deleting require a file there; and every existing path above
   * it must be a directory.
   */
  public sealed interface Mismatch extends Reason permits Missing, Occupied {

    /**
     * Returns what the operation requires at {@link #at()}.
     *
     * @return required kind
     */
    @NonNull
    Kind required();

    /**
     * Returns what {@link #at()} holds.
     *
     * @return found kind
     */
    @NonNull
    Kind found();

    @Override
    default String message() {
      return "Expected %s, found %s.".formatted(required().getPhrase(), found().getPhrase());
    }
  }

  /**
   * Nothing is at the operation's path, which requires a file there, and
   * nothing above it is in the way.
   *
   * @param address path the operation was on
   */
  public record Missing(@NonNull Address address) implements Mismatch {

    @Override
    public Address at() {
      return address;
    }

    @Override
    public Kind required() {
      return Kind.FILE;
    }

    @Override
    public Kind found() {
      return Kind.ABSENT;
    }
  }

  /**
   * Something is in the way: a file or directory where a file is to be
   * created, something other than a file where one is to be read, replaced or
   * deleted, or something other than a directory above the operation's path.
   *
   * @param address  path the operation was on
   * @param at       path that holds it: the operation's own, or one above it
   * @param required what the operation requires at that path
   * @param found    what that path holds
   */
  public record Occupied(
      @NonNull Address address,
      @NonNull Address at,
      @NonNull Kind required,
      @NonNull Kind found) implements Mismatch {

    /**
     * Checks that something other than what is required was found.
     *
     * @throws IllegalArgumentException when nothing was found, which is
     *                                  {@link Missing}, or what was required
     */
    public Occupied {
      if (found == Kind.ABSENT || found == required) {
        throw new IllegalArgumentException("An occupied path holds something other than is required.");
      }
    }

    /**
     * Describes the operation's own path holding the wrong kind of thing.
     *
     * @param address  path the operation was on
     * @param required what the operation requires there
     * @param found    what the path holds
     */
    public Occupied(@NonNull Address address, @NonNull Kind required, @NonNull Kind found) {
      this(address, address, required, found);
    }
  }

  /**
   * The filesystem does not permit the write.
   *
   * @param address  path the operation was on
   * @param at       the read-only file, or the directory that would hold it
   */
  public record Denied(@NonNull Address address, @NonNull Address at) implements Reason {

    @Override
    public String message() {
      return "Writing is not permitted.";
    }
  }

  /**
   * Another lease holds the operation's path, on a plate that does not wait.
   *
   * @param address path the operation was on
   */
  public record Held(@NonNull Address address) implements Reason {

    @Override
    public Address at() {
      return address;
    }

    @Override
    public String message() {
      return "Another lease holds the path.";
    }
  }
}
