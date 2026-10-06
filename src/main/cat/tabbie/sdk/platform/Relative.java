package cat.tabbie.sdk.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.NonNull;

/**
 * A logical path beneath a mount, held as canonical single-name components so
 * equality and validation are identical on every platform. {@link Root} lies
 * beneath the installation context; {@link World} lies beneath the selected
 * world. Physical containment, symlinks, and target filesystem case or name
 * restrictions are checked by the executor, not here.
 */
public sealed interface Relative permits Relative.Root, Relative.World {

	/**
	 * Returns the canonical components beneath the mount.
	 *
	 * @return immutable components, empty for the mount itself
	 */
	@NonNull
	List<String> components();

	/**
	 * Creates a path of the same kind of mount with other components.
	 *
	 * @param components canonical components beneath the same mount
	 * @return path of this kind
	 */
	@NonNull
	Relative with(@NonNull List<String> components);

	/**
	 * Names a path beneath the installation context.
	 *
	 * @param components single-name components, none for the mount itself
	 * @return context-scoped path
	 */
	static Relative.Root root(@NonNull String... components) {
		return new Root(List.of(components));
	}

	/**
	 * Names a path beneath the selected world.
	 *
	 * @param components single-name components, none for the mount itself
	 * @return world-scoped path
	 */
	static Relative.World world(@NonNull String... components) {
		return new World(List.of(components));
	}

	/**
	 * Appends single-name components beneath this path, keeping its mount.
	 *
	 * @param children single-name components
	 * @return descendant path of the same kind
	 */
	default Relative resolve(@NonNull String... children) {
		return resolve(List.of(children));
	}

	/**
	 * Appends single-name components beneath this path, keeping its mount.
	 *
	 * @param children single-name components
	 * @return descendant path of the same kind
	 * @throws IllegalArgumentException when a component is not a single
	 *                                  portable filename
	 */
	default Relative resolve(@NonNull List<String> children) {
		List<String> joined = new ArrayList<>(components());
		joined.addAll(children);
		return with(joined);
	}

	/**
	 * Appends a relative native path beneath this path, keeping its mount.
	 *
	 * @param child relative path that stays beneath this path
	 * @return descendant path of the same kind
	 * @throws IllegalArgumentException when the path is absolute or escapes
	 */
	default Relative resolve(@NonNull Path child) {
		return resolve(normalize(child));
	}

	/**
	 * Converts to a native relative path for an executor binding this mount.
	 *
	 * @return native path, empty for the mount itself
	 */
	default Path relativePath() {
		List<String> components = components();
		return components.isEmpty()
				? Path.of("")
				: Path.of(components.getFirst(), components.subList(1, components.size()).toArray(String[]::new));
	}

	/**
	 * Whether this names the mount itself rather than anything beneath it.
	 *
	 * @return true for the mount itself
	 */
	default boolean isMount() {
		return components().isEmpty();
	}

	/**
	 * The enclosing directory under the same mount.
	 *
	 * @return parent directory, or null for a top-level entry or the mount itself
	 */
	default Relative parent() {
		List<String> components = components();
		return components.size() < 2
				? null
				: with(components.subList(0, components.size() - 1));
	}

	/**
	 * Whether this lies strictly beneath a directory of the same mount. Every
	 * entry lies beneath the mount itself; nothing lies beneath itself.
	 *
	 * @param mount enclosing directory
	 * @return true when contained
	 */
	default boolean within(@NonNull Relative mount) {
		List<String> path = components();
		List<String> base = mount.components();
		return getClass() == mount.getClass()
				&& path.size() > base.size()
				&& path.subList(0, base.size()).equals(base);
	}

	/**
	 * Encodes the path with forward slashes, identically on every platform, for
	 * persistence. The kind of mount is not included; serializers record it
	 * separately.
	 *
	 * @return forward-slash encoding, empty for the mount itself
	 */
	default String encoded() {
		return String.join("/", components());
	}

	/**
	 * Decodes a persisted forward-slash path. Only canonical encodings are
	 * accepted: empty components, dot segments, and backslashes are rejected
	 * rather than normalized, so a persisted path decodes to identical
	 * components on every platform.
	 *
	 * @param encoded forward-slash encoding, empty for a mount
	 * @return immutable canonical components
	 * @throws IllegalArgumentException when the encoding is not canonical
	 */
	static List<String> decode(@NonNull String encoded) {
		if (encoded.isEmpty()) {
			return List.of();
		}
		return canonical(Arrays.asList(encoded.split("/", -1)));
	}

	/**
	 * Validates a logical relative native path and normalizes internal dot
	 * segments. Empty paths name a mount itself.
	 *
	 * @param path logical path
	 * @return immutable canonical components contained within the mount
	 * @throws IllegalArgumentException when the path is absolute or escapes
	 */
	static List<String> normalize(@NonNull Path path) {
		Path normalized = path.normalize();
		if (path.getRoot() != null || path.isAbsolute() || normalized.startsWith("..")) {
			throw new IllegalArgumentException("A logical path must stay beneath its relative mount.");
		}
		if (normalized.toString().isEmpty()) {
			return List.of();
		}
		List<String> components = new ArrayList<>();
		for (Path component : normalized) {
			components.add(component.toString());
		}
		return canonical(components);
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
	static List<String> canonical(@NonNull List<String> components) {
		List<String> copy = List.copyOf(components);
		for (String component : copy) {
			validateFilename(component);
		}
		return copy;
	}

	/**
	 * Checks a single filename without reading the filesystem. These lexical
	 * checks do not establish whether a target filesystem permits the name.
	 *
	 * @param fileName filename to check
	 * @throws IllegalArgumentException when the name is blank or contains a path
	 */
	static void validateFilename(@NonNull String fileName) {
		if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
				|| fileName.chars().anyMatch(character -> character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0)) {
			throw new IllegalArgumentException("Expected a non-blank single filename.");
		}
	}

	/**
	 * A path beneath the installation context, which is itself relative to the
	 * managed installation's working directory.
	 *
	 * @param components canonical components, empty for the context mount
	 */
	record Root(@NonNull List<String> components) implements Relative {
		/**
		 * Validates and copies the components.
		 */
		public Root {
			components = canonical(components);
		}

		@Override
		public Root with(@NonNull List<String> components) {
			return new Root(components);
		}

		@Override
		public Root resolve(@NonNull String... children) {
			return (Root) Relative.super.resolve(children);
		}

		@Override
		public Root resolve(@NonNull List<String> children) {
			return (Root) Relative.super.resolve(children);
		}

		@Override
		public Root resolve(@NonNull Path child) {
			return (Root) Relative.super.resolve(child);
		}
	}

	/**
	 * A path beneath the selected world mount.
	 *
	 * @param components canonical components, empty for the world mount
	 */
	record World(@NonNull List<String> components) implements Relative {
		/**
		 * Validates and copies the components.
		 */
		public World {
			components = canonical(components);
		}

		@Override
		public World with(@NonNull List<String> components) {
			return new World(components);
		}

		@Override
		public World resolve(@NonNull String... children) {
			return (World) Relative.super.resolve(children);
		}

		@Override
		public World resolve(@NonNull List<String> children) {
			return (World) Relative.super.resolve(children);
		}

		@Override
		public World resolve(@NonNull Path child) {
			return (World) Relative.super.resolve(child);
		}
	}
}