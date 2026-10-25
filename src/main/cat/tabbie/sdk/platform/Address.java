package cat.tabbie.sdk.platform;

import java.io.Serial;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * A canonical relative path, held as single-name components so equality and
 * validation are identical on every platform. What it is relative to depends
 * on where it is used: an image's address lies beneath the Cat, a source's
 * path beneath its source, and an archive entry's path beneath its archive.
 * Work that needs a path in a particular directory {@link #require(Address)
 * requires} it there. Physical locations, links, and a target filesystem's
 * case and name restrictions are checked by whoever binds the path, not here.
 *
 * @param components canonical components, empty for the base itself
 */
public record Address(@NonNull List<String> components) {

  /**
   * Validates and copies the components.
   *
   * @throws IllegalArgumentException when a component is not a single
   *                                  portable filename
   */
  public Address {
    components = List.copyOf(components);
    components.forEach(Address::validateFilename);
  }

  /**
   * Addresses the base itself, such as the Cat.
   *
   * @return the root address
   */
  public static Address root() {
    return new Address(List.of());
  }

  /**
   * Addresses a path beneath the base.
   *
   * @param components single-name components, none for the base itself
   * @return the address
   * @throws IllegalArgumentException when a component is not a single
   *                                  portable filename
   */
  public static Address of(@NonNull String... components) {
    return new Address(List.of(components));
  }

  /**
   * Reads the {@link #canonical() canonical} forward-slash form of an address.
   * Only canonical text is accepted: empty components, dot segments, and
   * backslashes are rejected rather than normalized, so the same text reads as
   * the same address on every platform.
   *
   * @param canonical forward-slash form, empty for the base itself
   * @return the address
   * @throws IllegalArgumentException when the text is not canonical
   */
  public static Address parse(@NonNull String canonical) {
    return canonical.isEmpty()
        ? root()
        : new Address(Arrays.asList(canonical.split("/", -1)));
  }

  /**
   * Checks a single filename without reading the filesystem. These lexical
   * checks do not establish whether a target filesystem permits the name.
   *
   * @param fileName filename to check
   * @throws IllegalArgumentException when the name is blank or contains a path
   */
  public static void validateFilename(@NonNull String fileName) {
    if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
        || fileName.chars().anyMatch(character -> character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0)) {
      throw new IllegalArgumentException("Expected a non-blank single filename.");
    }
  }

  /**
   * Appends single-name components beneath this address.
   *
   * @param children single-name components
   * @return descendant address
   * @throws IllegalArgumentException when a component is not a single
   *                                  portable filename
   */
  public Address resolve(@NonNull String... children) {
    return resolve(List.of(children));
  }

  /**
   * Appends single-name components beneath this address.
   *
   * @param children single-name components
   * @return descendant address
   * @throws IllegalArgumentException when a component is not a single
   *                                  portable filename
   */
  public Address resolve(@NonNull List<String> children) {
    List<String> joined = new ArrayList<>(components);
    joined.addAll(children);
    return new Address(joined);
  }

  /**
   * Appends another address beneath this one, such as an archive entry's path
   * beneath the directory it is unpacked into.
   *
   * @param child address relative to this one
   * @return descendant address
   */
  public Address resolve(@NonNull Address child) {
    return resolve(child.components());
  }

  /**
   * Requires this address to lie in a directory: to be the directory itself,
   * or anywhere beneath it. It is the check that matches
   * {@link #resolve(String...) resolve}, which goes down from a directory.
   *
   * @param directory directory this address must lie in
   * @return this address
   * @throws Outside when this address lies elsewhere
   */
  public Address require(@NonNull Address directory) {
    if (!equals(directory) && !within(directory)) {
      throw new Outside(this, directory);
    }
    return this;
  }

  /**
   * Returns the last component, the name of what this address names.
   *
   * @return file or directory name, or empty for the base itself
   */
  public Optional<String> fileName() {
    return isRoot()
        ? Optional.empty()
        : Optional.of(components.getLast());
  }

  /**
   * Returns the enclosing directory.
   *
   * @return the parent address, or empty for the base itself
   */
  public Optional<Address> parent() {
    return isRoot()
        ? Optional.empty()
        : Optional.of(new Address(components.subList(0, components.size() - 1)));
  }

  /**
   * Whether this addresses the base itself rather than anything beneath it.
   *
   * @return true for the root address
   */
  public boolean isRoot() {
    return components.isEmpty();
  }

  /**
   * Whether this lies strictly beneath a directory. Every address but the root
   * lies beneath the root; nothing lies beneath itself.
   *
   * @param directory enclosing directory
   * @return true when contained
   */
  public boolean within(@NonNull Address directory) {
    return components.size() > directory.components().size()
        && components.subList(0, directory.components().size()).equals(directory.components());
  }

  /**
   * Converts to a native relative path, for whoever binds the base to a
   * directory.
   *
   * @return native path, empty for the base itself
   */
  public Path relativePath() {
    return isRoot()
        ? Path.of("")
        : Path.of(components.getFirst(), components.subList(1, components.size()).toArray(String[]::new));
  }

  /**
   * Returns the stable forward-slash form of this address, identical on every
   * platform, for persistence. {@link #parse(String)} reads it back.
   *
   * @return forward-slash form, empty for the base itself
   */
  public String canonical() {
    return String.join("/", components);
  }

  /**
   * Thrown when an address does not lie in the directory work requires it in.
   */
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public static final class Outside extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    transient Address address;

    transient Address directory;

    /**
     * Describes an address outside the directory it must lie in.
     *
     * @param address   address that lies elsewhere
     * @param directory directory it must lie in
     */
    public Outside(@NonNull Address address, @NonNull Address directory) {
      super("%s does not lie in %s.".formatted(shown(address), shown(directory)));
      this.address = address;
      this.directory = directory;
    }

    /**
     * Returns the address that lies elsewhere.
     *
     * @return address
     */
    public Address address() {
      return address;
    }

    /**
     * Returns the directory the address must lie in.
     *
     * @return required directory
     */
    public Address directory() {
      return directory;
    }

    /**
     * Shows an address in a message, naming the base itself for the root.
     */
    private static String shown(Address address) {
      return address.isRoot() ? "the root" : address.canonical();
    }
  }
}
