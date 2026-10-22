package cat.tabbie.sdk.album.model;

import java.io.Serial;
import java.nio.file.FileSystemException;

import cat.tabbie.sdk.platform.Relative;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Thrown when a plate will not carry out an operation, and changed nothing.
 * {@link #reason()} says why. Failures while reading or writing are other
 * IOExceptions.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Refusal extends FileSystemException {

  @Serial
  private static final long serialVersionUID = 1L;

  transient Reason reason;

  /**
   * Refuses an operation.
   *
   * @param reason why
   */
  public Refusal(@NonNull Reason reason) {
    super(reason.relative().encoded(), reason().above() ? reason().at() : null, reason.message());
    this.reason = reason;
  }

  /**
   * Why the plate refused.
   *
   * @return refusal reason
   */
  public Reason reason() {
    return reason;
  }

  /**
   * What a path holds.
   */
  public enum Kind {

    ABSENT,

    FILE,

    DIRECTORY,

    OTHER
  }

  /**
   * Why a plate refused. Every reason names the path the operation was on and
   * the path that decided it: the operation's own path when anything is there,
   * otherwise the nearest existing path above it.
   */
  public sealed interface Reason {

    @NonNull
    Relative relative();

    @NonNull
    Relative at();

    @NonNull
    String message();

    default boolean above() {
      return !at().equals(relative());
    }
  }

  /**
   * The path that decided holds something other than the operation requires
   * there.
   */
  public sealed interface Mismatch extends Reason {

    @NonNull
    Kind required();

    @NonNull
    Kind found();

    @Override
    default String message() {
      return "Expected %s, found %s.".formatted(required(), found());
    }
  }

  /**
   * Nothing is at the operation's path, which requires a file there.
   *
   * @param relative path the operation was on
   */
  public record Missing(@NonNull Relative relative) implements Mismatch {

    @Override
    public Relative at() {
      return relative;
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
   * Something is in the way at the operation's path, or above it.
   *
   * @param relative path the operation was on
   * @param at       path that holds it: the operation's own, or one above it
   * @param required what the operation requires at that path
   * @param found    what that path holds
   */
  public record Occupied(
      @NonNull Relative relative,
      @NonNull Relative at,
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
     * @param relative path the operation was on
     * @param required what the operation requires there
     * @param found    what the path holds
     */
    public Occupied(@NonNull Relative relative, @NonNull Kind required, @NonNull Kind found) {
      this(relative, relative, required, found);
    }
  }

  /**
   * The filesystem does not permit the write.
   *
   * @param relative path the operation was on
   * @param at       the read-only file, or the directory that would hold it
   */
  public record Denied(@NonNull Relative relative, @NonNull Relative at) implements Reason {

    @Override
    public String message() {
      return "access denied for this location";
    }
  }

  /**
   * Another lease holds the operation's path, on a plate that does not wait.
   *
   * @param relative path the operation was on
   */
  public record Held(@NonNull Relative relative) implements Reason {

    @Override
    public Relative at() {
      return relative;
    }

    @Override
    public String message() {
      return "lock held for this location";
    }
  }
}
