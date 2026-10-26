package cat.tabbie.sdk.platform.layout;

import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

import cat.tabbie.sdk.platform.Address;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Thrown when work asks a tree of {@link Node nodes} for something it does not
 * declare or allow. It is found while describing work, before anything is
 * carried out, so nothing has changed. It is the layout's counterpart of a
 * plate's {@link cat.tabbie.sdk.album.model.Refusal Refusal}, which refuses an
 * allowed operation when it is carried out.
 */
public abstract sealed class Violation extends IllegalArgumentException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Describes a violation.
   *
   * @param message sentence stating what the layout does not declare or allow
   */
  protected Violation(@NonNull String message) {
    super(message);
  }

  /**
   * Shows an address in a message, naming the root itself.
   */
  private static String shown(Address address) {
    return address.isRoot() ? "the root" : address.canonical();
  }

  /**
   * An address leads beneath a declared file, where nothing can lie.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Undeclared extends Violation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    String name;

    /**
     * Describes a step beneath a declared file.
     *
     * @param address address of the file
     * @param name    the name beneath it
     */
    public Undeclared(@NonNull Address address, @NonNull String name) {
      super("Nothing can lie beneath the file %s, such as %s.".formatted(shown(address), name));
      this.address = address;
      this.name = name;
    }

    /**
     * Returns the address of the file.
     *
     * @return file address
     */
    public Address address() {
      return address;
    }

    /**
     * Returns the name beneath the file.
     *
     * @return undeclared name
     */
    public String name() {
      return name;
    }
  }

  /**
   * A node does not hold the permission an operation on it needs.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Forbidden extends Violation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    Rule.Permission permission;

    /**
     * Describes a missing permission.
     *
     * @param address    address of the operation
     * @param permission permission it needs
     */
    public Forbidden(@NonNull Address address, @NonNull Rule.Permission permission) {
      super("The layout does not make %s %s.".formatted(shown(address), permission));
      this.address = address;
      this.permission = permission;
    }

    /**
     * Returns the address of the operation.
     *
     * @return address
     */
    public Address address() {
      return address;
    }

    /**
     * Returns the permission the operation needs.
     *
     * @return missing permission
     */
    public Rule.Permission permission() {
      return permission;
    }
  }

  /**
   * Content is placed where the layout does not accept its type.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Unaccepted extends Violation {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    Class<?> type;

    /**
     * Describes content of a type placed where it is not accepted.
     *
     * @param address address of the placement
     * @param type    type of the content
     */
    public Unaccepted(@NonNull Address address, @NonNull Class<?> type) {
      super("The layout does not accept %s at %s.".formatted(type.getSimpleName(), shown(address)));
      this.address = address;
      this.type = type;
    }

    /**
     * Returns the address of the placement.
     *
     * @return address
     */
    public Address address() {
      return address;
    }

    /**
     * Returns the type of the content.
     *
     * @return content type
     */
    public Class<?> type() {
      return type;
    }
  }

  /**
   * No declared directory accepts content of a type, so there is nowhere to
   * place it.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Missing extends Violation {

    @Serial
    private static final long serialVersionUID = 1L;

    Class<?> type;

    /**
     * Describes content of a type that no directory accepts.
     *
     * @param type type of the content
     */
    public Missing(@NonNull Class<?> type) {
      super("The layout declares no directory accepting %s.".formatted(type.getSimpleName()));
      this.type = type;
    }

    /**
     * Returns the type of the content.
     *
     * @return content type
     */
    public Class<?> type() {
      return type;
    }
  }

  /**
   * Several declared directories accept content of a type, where the work
   * placing it required exactly one.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Ambiguous extends Violation {

    @Serial
    private static final long serialVersionUID = 1L;

    Class<?> type;

    transient List<Address> addresses;

    /**
     * Describes content of a type that several directories accept.
     *
     * @param type      type of the content
     * @param addresses addresses of the accepting directories
     */
    public Ambiguous(@NonNull Class<?> type, @NonNull List<Address> addresses) {
      super("The layout declares several directories accepting %s: %s.".formatted(
          type.getSimpleName(),
          addresses.stream()
              .map(Violation::shown)
              .collect(Collectors.joining(", "))));
      this.type = type;
      this.addresses = List.copyOf(addresses);
    }

    /**
     * Returns the type of the content.
     *
     * @return content type
     */
    public Class<?> type() {
      return type;
    }

    /**
     * Returns the addresses of the accepting directories.
     *
     * @return immutable addresses, in declaration order
     */
    public List<Address> addresses() {
      return addresses;
    }
  }
}
