package cat.tabbie.sdk.platform.layout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.NonNull;

/**
 * A path beneath a Cat, held as canonical single-name components so equality
 * and validation are identical on every platform. Work that needs a path in a
 * particular directory, such as the selected World or a mods directory,
 * {@link #require(Address) requires} it there. Where the Cat lies physically,
 * and the target filesystem's links, case and name restrictions, are checked
 * by whoever binds the Cat, not here.
 *
 * @param components canonical components beneath the Cat, empty for the Cat
 *                   itself
 */
public record Address(@NonNull List<String> components) {

  /**
   * Validates and copies the components.
   *
   * @throws IllegalArgumentException when a component is not a single
   *                                  portable filename
   */
  public Address {
    components = canonical(components);
  }

  /**
   * Addresses the Cat itself.
   *
   * @return the root address
   */
  public static Address root() {
    return new Address(List.of());
  }

  /**
   * Addresses a path beneath the Cat.
   *
   * @param components single-name components, none for the Cat itself
   * @return the address
   * @throws IllegalArgumentException when a component is not a single
   *                                  portable filename
   */
  public static Address of(@NonNull String... components) {
    return new Address(List.of(components));
  }

  /**
   * Decodes a persisted forward-slash address. Only canonical encodings are
   * accepted, so a persisted address decodes to identical components on every
   * platform.
   *
   * @param encoded forward-slash encoding, empty for the Cat itself
   * @return the address
   * @throws IllegalArgumentException when the encoding is not canonical
   */
  public static Address parse(@NonNull String encoded) {
    return new Address(decode(encoded));
  }

  /**
   * Decodes a persisted forward-slash path into its components. Only canonical
   * encodings are accepted: empty components, dot segments, and backslashes
   * are rejected rather than normalized, so a persisted path decodes to
   * identical components on every platform.
   *
   * @param encoded forward-slash encoding, empty for no components
   * @return immutable canonical components
   * @throws IllegalArgumentException when the encoding is not canonical
   */
  public static List<String> decode(@NonNull String encoded) {
    return encoded.isEmpty()
        ? List.of()
        : canonical(Arrays.asList(encoded.split("/", -1)));
  }

  /**
   * Checks that every component is one portable filename.
   *
   * @param components candidate components
   * @return immutable copy
   * @throws IllegalArgumentException when a component is empty, a dot segment,
   *                                  or contains a separator or reserved
   *                                  character
   */
  public static List<String> canonical(@NonNull List<String> components) {
    List<String> copy = List.copyOf(components);
    copy.forEach(Address::validateFilename);
    return copy;
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
   * Requires this address to lie in a directory: to be the directory itself,
   * or anywhere beneath it. It is the check that matches
   * {@link #resolve(String...) resolve}, which goes down from a directory.
   *
   * @param directory directory this address must lie in
   * @return this address
   * @throws Violation.Outside when this address lies elsewhere
   */
  public Address require(@NonNull Address directory) {
    if (!equals(directory) && !within(directory)) {
      throw new Violation.Outside(this, directory);
    }
    return this;
  }

  /**
   * Returns the enclosing directory.
   *
   * @return the parent address, or empty for the Cat itself
   */
  public Optional<Address> parent() {
    return isRoot()
        ? Optional.empty()
        : Optional.of(new Address(components.subList(0, components.size() - 1)));
  }

  /**
   * Whether this addresses the Cat itself rather than anything beneath it.
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
   * Converts to a native relative path, for whoever binds the Cat to a
   * directory.
   *
   * @return native path, empty for the Cat itself
   */
  public Path relativePath() {
    return isRoot()
        ? Path.of("")
        : Path.of(components.getFirst(), components.subList(1, components.size()).toArray(String[]::new));
  }

  /**
   * Encodes the address with forward slashes, identically on every platform,
   * for persistence.
   *
   * @return forward-slash encoding, empty for the Cat itself
   */
  public String encoded() {
    return String.join("/", components);
  }
}
