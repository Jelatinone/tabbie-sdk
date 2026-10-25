package cat.tabbie.sdk.album.revision;

import java.io.Serial;

import cat.tabbie.sdk.platform.Address;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Thrown when an {@link Image} cannot be described as requested, because the
 * change would be malformed. An image that exists is never malformed:
 * constructing, composing, reducing and validating images throw a subtype
 * that says exactly why instead of producing one. It is the description-time
 * counterpart of a plate's {@link cat.tabbie.sdk.album.model.Refusal Refusal},
 * which refuses a well-formed change when it is carried out.
 */
public abstract sealed class Malformation extends IllegalArgumentException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Describes a malformed change.
   *
   * @param message sentence stating the requirement the change breaks
   * @param cause   failure that revealed it, or null
   */
  protected Malformation(@NonNull String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * The change's before and after states are the same, so it changes nothing.
   * Both sides absent, equal content, and chunks that only hold context are
   * all unchanged.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Unchanged extends Malformation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    /**
     * Describes a change that changes nothing.
     *
     * @param address file the change was on
     */
    public Unchanged(@NonNull Address address) {
      super("A change of %s must change something.".formatted(address.canonical()), null);
      this.address = address;
    }

    /**
     * Returns the file the change was on.
     *
     * @return changed file
     */
    public Address address() {
      return address;
    }
  }

  /**
   * The change names the root itself rather than a file beneath it.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Root extends Malformation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    /**
     * Describes a change of the root itself.
     *
     * @param address the root's address the change named
     */
    public Root(@NonNull Address address) {
      super("A changed file must lie beneath its root.", null);
      this.address = address;
    }

    /**
     * Returns the address the change named.
     *
     * @return the root's address
     */
    public Address address() {
      return address;
    }
  }

  /**
   * A later change of a file does not continue from the state an earlier
   * change left it in, so the two cannot be applied one after the other.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Discontinuous extends Malformation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    /**
     * Describes discontinuous changes.
     *
     * @param address file the changes were on
     */
    public Discontinuous(@NonNull Address address) {
      this(address, null);
    }

    /**
     * Describes discontinuous changes found by a failure, such as chunks that
     * disagree about the text between them.
     *
     * @param address file the changes were on
     * @param cause   failure that revealed the discontinuity
     */
    public Discontinuous(@NonNull Address address, Throwable cause) {
      super("Changes of %s must continue from one another.".formatted(address.canonical()), cause);
      this.address = address;
    }

    /**
     * Returns the file the changes were on.
     *
     * @return changed file
     */
    public Address address() {
      return address;
    }
  }

  /**
   * Changes of different files were composed; only changes of one file
   * compose.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Unrelated extends Malformation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address first;

    transient Address second;

    /**
     * Describes a composition of changes of different files.
     *
     * @param first  file of the earlier change
     * @param second file of the later change
     */
    public Unrelated(@NonNull Address first, @NonNull Address second) {
      super("Only changes of one file compose, not %s and %s.".formatted(first.canonical(), second.canonical()), null);
      this.first = first;
      this.second = second;
    }

    /**
     * Returns the file of the earlier change.
     *
     * @return earlier file
     */
    public Address first() {
      return first;
    }

    /**
     * Returns the file of the later change.
     *
     * @return later file
     */
    public Address second() {
      return second;
    }
  }

  /**
   * Two images of one set change the same file, so the set cannot be exposed
   * in any order.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Clash extends Malformation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    /**
     * Describes two images of one file in a set.
     *
     * @param address file both images change
     */
    public Clash(@NonNull Address address) {
      super("A set may change %s only once.".formatted(address.canonical()), null);
      this.address = address;
    }

    /**
     * Returns the file both images change.
     *
     * @return changed file
     */
    public Address address() {
      return address;
    }
  }

  /**
   * One image of a set changes a file that another image's file lies beneath,
   * so the path would have to be both a file and a directory.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Nested extends Malformation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address directory;

    transient Address file;

    /**
     * Describes a file that a set also uses as a directory.
     *
     * @param directory changed file that another changed file lies beneath
     * @param file      changed file beneath it
     */
    public Nested(@NonNull Address directory, @NonNull Address file) {
      super("%s cannot be a file while %s lies beneath it.".formatted(directory.canonical(), file.canonical()), null);
      this.directory = directory;
      this.file = file;
    }

    /**
     * Returns the changed file another changed file lies beneath.
     *
     * @return file used as a directory
     */
    public Address directory() {
      return directory;
    }

    /**
     * Returns the changed file beneath it.
     *
     * @return nested file
     */
    public Address file() {
      return file;
    }
  }
}
