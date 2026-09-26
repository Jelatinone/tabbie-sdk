package cat.tabbie.sdk.platform;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.StringJoiner;

import cat.tabbie.sdk.album.repository.Reference;
import lombok.NonNull;

public sealed interface Relative {

  @NonNull
  Path relativePath();

  @NonNull
  Relative resolve(Path relativePath);

  /**
   * 
   * @param path
   * @return
   */
  static Relative.Root root(@NonNull String path) {
    return new Root(Path.of(path));
  }

  /**
   * 
   * @param path
   * @param add
   * @return
   */
  static Relative.Root root(@NonNull String path, @NonNull String... add) {
    return new Root(Path.of(path, add));
  }

  /**
   * 
   * @param path
   * @return
   */
  static Relative.World world(@NonNull String path) {
    return new World(Path.of(path));
  }

  /**
   * 
   * @param path
   * @param add
   * @return
   */
  static Relative.World world(@NonNull String path, @NonNull String... add) {
    return new World(Path.of(path, add));
  }

  @NonNull
  default Relative resolve(String relativePath) {
    return resolve(Path.of(relativePath));
  }

  /**
   * Whether this names the mount itself rather than anything beneath it.
   *
   * @return true for the mount itself
   */
  default boolean isMount() {
    return relativePath().toString().isEmpty();
  }

  /**
   * The enclosing directory under the same mount.
   *
   * @return parent directory, or null for a top-level entry or the mount itself
   */
  default Relative parent() {
    Path parent = relativePath().getParent();
    return parent == null
        ? null
        : resolve(parent);
  }

  /**
   * Whether this lies strictly beneath a directory of the same mount. Every
   * entry lies beneath the mount itself; nothing lies beneath itself.
   *
   * @param mount enclosing directory
   * @return true when contained
   */
  default boolean within(@NonNull Relative mount) {
    Path path = relativePath();
    Path base = mount.relativePath();
    return getClass() == mount.getClass() && !isMount()
        && (mount.isMount() || (path.startsWith(base) && !path.equals(base)));
  }

  /**
   * Encodes the path with forward slashes, identically on every platform, for
   * persistence. The kind of mount is not included; serializers record it
   * separately.
   *
   * @return forward-slash encoding, empty for the mount itself
   */
  default String encoded() {
    StringJoiner joiner = new StringJoiner("/");
    if (!isMount()) {
      for (Path component : relativePath())
        joiner.add(component.toString());
    }
    return joiner.toString();
  }

  /**
   * Decodes a persisted forward-slash path. Only canonical encodings are
   * accepted: empty components, dot segments, and backslashes are rejected
   * rather than normalized, so a persisted path decodes to identical
   * components on every platform.
   *
   * @param encoded forward-slash encoding
   * @return platform path with identical logical components
   * @throws IllegalArgumentException when the encoding is not canonical
   */
  static Path decode(@NonNull String encoded) {
    if (encoded.isEmpty()) {
      return Path.of("");
    }
    String[] components = encoded.split("/", -1);
    for (String component : components) {
      if (component.isEmpty() || component.equals(".") || component.equals("..")
          || component.indexOf('\\') >= 0) {
        throw new IllegalArgumentException("Not a canonical encoded path: " + encoded);
      }
      Reference.validateFilename(component);
    }
    return Path.of(components[0], Arrays.copyOfRange(components, 1, components.length));
  }

  /**
   * Validates a logical relative path and normalizes internal dot segments.
   * Empty paths name a mount itself. This does not inspect physical
   * containment, symlinks, or target filesystem case/name restrictions; the
   * agent checks those.
   *
   * @param path logical path
   * @return normalized path contained within its mount
   */
  static Path normalize(@NonNull Path path) {
    Path normalized = path.normalize();
    if (path.getRoot() != null || path.isAbsolute() || normalized.startsWith("..")) {
      throw new IllegalArgumentException("A logical path must stay beneath its relative mount.");
    }
    if (!normalized.toString().isEmpty()) {
      for (Path component : normalized)
        Reference.validateFilename(component.toString());
    }
    return normalized;
  }

  record Root(@NonNull Path relativePath) implements Relative {
    public Root {
      relativePath = normalize(relativePath);
    }

    @Override
    public @NonNull Relative resolve(Path resolveTo) {
      return new Root(resolveTo);
    }
  }

  record World(@NonNull Path relativePath) implements Relative {
    public World {
      relativePath = normalize(relativePath);
    }

    @Override
    public @NonNull Relative resolve(Path resolveTo) {
      return new Root(resolveTo);
    }
  }

}
