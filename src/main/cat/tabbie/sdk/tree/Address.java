package cat.tabbie.sdk.tree;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import cat.tabbie.sdk.failure.Reason;
import cat.tabbie.sdk.failure.Violation;
import lombok.NonNull;

/**
 * A canonical relative path, held as single-name components so equality and
 * validation are identical on every platform. What it is relative to depends
 * on where it is used: an image's address lies beneath the Cat, a source's
 * path beneath its source, and an archive entry's path beneath its archive.
 * Whether a path lies beneath a directory is {@link #within(Address)}, and
 * {@link #relativeTo(Address)} takes it back out of one.
 *
 * <p>
 * Every component is a portable filename, valid on every platform tabbie
 * runs on, so a Cat's history replays anywhere. Case is kept as written and
 * compared exactly; whether names differing only by case may sit side by side
 * is the tree's to say. Physical locations and links are checked by whoever
 * binds the path, not here.
 *
 * @param components canonical components, empty for the base itself
 */
public record Address(@NonNull List<String> components) {

  /**
   * Windows' device names, which no file may take, whatever follows the first
   * dot.
   */
  private static final Pattern DEVICE_NAME = Pattern.compile("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");

  /**
   * Validates and copies the components.
   *
   * @throws IllegalArgumentException when a component is not a single
   *                                  portable filename
   */
  public Address {
    components = List.copyOf(components);
    for (String component : components) {
      if (!portable(component)) {
        throw new IllegalArgumentException(String.format("\"%s\" is not a portable single filename.", component));
      }
    }
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
   * Whether a single filename is portable, judged without reading the
   * filesystem: not blank, not a dot segment, with no control character or
   * character Windows reserves, not one of Windows' device names ({@code CON},
   * {@code PRN}, {@code AUX}, {@code NUL}, {@code COM1} to {@code COM9},
   * {@code LPT1} to {@code LPT9}, in any case, with or without an extension),
   * and not ending in a dot or a space. Whether a target filesystem has room
   * for the name is not judged. Every name in this package that a shape or its
   * bytes are known by is checked by it.
   */
  static boolean portable(String name) {
    String stem = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
    return !name.isBlank() && !name.equals(".") && !name.equals("..")
        && name.chars().noneMatch(character -> character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0)
        && !name.endsWith(".") && !name.endsWith(" ")
        && !DEVICE_NAME.matcher(stem).matches();
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
   * Returns this address relative to a directory it lies in, undoing
   * {@link #resolve(Address) resolve} from that directory: this address is the
   * directory itself, or {@link #within(Address) within} it.
   *
   * @param directory directory this address lies in
   * @return address beneath the directory, the root address for the
   *         directory itself
   * @throws Violation with {@link Reason.Place.Outside} when this address lies
   *                   elsewhere
   */
  public Address relativeTo(@NonNull Address directory) {
    if (!equals(directory) && !within(directory)) {
      throw new Violation(new Reason.Place.Outside(this, directory));
    }
    return new Address(components.subList(directory.components().size(), components.size()));
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
   * Shows this address to a person, such as in a failure's message: its
   * {@link #canonical() canonical} form, or "the root" for the base itself.
   *
   * @return text naming this address
   */
  public String shown() {
    return isRoot() ? "the root" : canonical();
  }
}
