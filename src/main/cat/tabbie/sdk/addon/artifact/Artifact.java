package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Reference;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;

import lombok.NonNull;

/**
 * 
 * <h1>Artifact</h1>
 * 
 * <p>
 * Described artifact content backed by a supplied store sink, without access to
 * the target filesystem.
 * </p>
 * 
 */
public interface Artifact {

	Artist<Artifact> DEFAULT_ARTIST = new Artist.Default();

	/**
	 * Stable artifact identity
	 *
	 * @return artifact identity
	 */
	@NonNull
	Identity<Artifact> artifactId();

	/**
	 * Human-readable canonical artifact name
	 * 
	 * @return artifact name
	 */
	@NonNull
	String artifactName();

	/**
	 * Delegated storage receiving capture/open calls
	 * 
	 * @return delegated storage
	 */
	Store store();

	/**
	 * Declares support for the entire artifact.
	 *
	 * @return immutable, nonempty supported targets
	 */
	@NonNull
	Set<Label> labels();

	/**
	 * Artifact identities that must be installed alongside this artifact
	 * 
	 * @return depending artifact identities
	 */
	@NonNull
	Set<Identity<Artifact>> depends();

	/**
	 * Artifact identities that may not be installed alongside this artifact
	 * 
	 * @return conflicting artifact identities
	 */
	@NonNull
	Set<Identity<Artifact>> conflicts();

	/**
	 * Payload installation images relative to this type's installation root.
	 * 
	 * @return installation images
	 */
	@NonNull
	Set<Image<?>> images(@NonNull Context context) throws IOException;

	/**
	 * Checks copied constructor values.
	 *
	 * @throws IllegalArgumentException when declarations contradict structural
	 *                                  facts
	 */
	default void validate() {
		if (artifactName().isBlank() || labels().isEmpty()) {
			throw new IllegalArgumentException("An artifact needs a non-blank name and supported labels.");
		}
		if (labels().stream().anyMatch(label -> !label.distribution().capabilities().contains(getClass()))) {
			throw new IllegalArgumentException("A declared distribution does not support this artifact family.");
		}
		if (depends().contains(artifactId()) || conflicts().contains(artifactId())
				|| depends().stream().anyMatch(conflicts()::contains)) {
			throw new IllegalArgumentException("External relationships must not reference self or contradict each other.");
		}
	}

	@FunctionalInterface
	interface Artist<Canvas extends Artifact> {

		Set<Image<?>> paint(@NonNull Canvas artifact, @NonNull Context context) throws IOException;

		record Default() implements Artist<Artifact> {

			@Override
			public Set<Image<?>> paint(@NonNull Artifact artifact, @NonNull Context context) throws IOException {
				try (InputStream stream = context.store().open()) {
					Reference captured = context.store()
							.capture(stream);
					Artifact.Layout layout = context.label()
							.distribution()
							.layout(artifact, context);

					// TODO: Unpacking

					Image<?> image = Image.create(
							layout.relativePath()
									.resolve(captured.fileName()),
							captured);
					return Set.of(image);
				}
			}

		}
	}

	interface Context {

		@NonNull
		Label label();

		@NonNull
		Store store();

		@NonNull
		Path relativePath();

		record Default(Label label, Store store, Path relativePath) implements Context {
			public Default {
				if (relativePath.isAbsolute()) {
					throw new IllegalArgumentException("Context root must be relative");
				}
				relativePath = relativePath.normalize();
			}
		}
	}

	sealed interface Layout
			permits Layout.Root, Layout.World {

		boolean unpack();

		@NonNull
		Path relativePath();

		record Root(boolean unpack, Path relativePath) implements Layout {

			public Root {
				relativePath = validate(relativePath);
			}
		}

		record World(boolean unpack, Path relativePath) implements Layout {

			public World {
				relativePath = validate(relativePath);
			}
		}

		private static Path validate(Path path) {
			if (path.isAbsolute()) {
				throw new IllegalArgumentException(
						"Layout path must be relative");
			}

			return path.normalize();
		}
	}
}
