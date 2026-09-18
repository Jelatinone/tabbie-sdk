package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Reference;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Compatibility;
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
	 * Declared own reference file, excluding dependencies or other stored
	 * content beyond what would produced by related {@link Image.File file}.
	 * operations.
	 * 
	 * It may reflect that the required reference type has already been captured via
	 * {@link Reference.Captured Captured}.
	 * 
	 * @return artifact reference
	 */
	Reference artifactReference();

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

	final Artist<Artifact> DEFAULT = (artifact, context) -> {
		Artifact.Layout layout = context.label()
				.distribution()
				.layout(artifact, context);

		InputStream stream = context.store()
				.open(artifact.artifactReference());

		Reference.Captured captured = context.store()
				.capture(artifact.artifactReference().fileName(), stream);

		// TODO: Unpack zips, safety checks

		Image<?> image = Image.create(
				layout.directory()
						.resolve(artifact.artifactReference().fileName()),
				captured);

		return Set.of(image);
	};

	interface Artist<Canvas extends Artifact> {

		Set<Image<?>> paint(Canvas artifact, Context context) throws IOException;
	}

	interface Context {

		@NonNull
		Label label();

		@NonNull
		Path root();

		@NonNull
		Store store();

		record Default(Label label, Path root, Store store) implements Context {
		}
	}

	interface Layout {

		@NonNull
		Path directory();

		boolean unpack();

		record Default(Path directory, boolean unpack) implements Layout {
		}
	}

	/**
	 * Assesses type support followed by explicit target declarations. Unlisted
	 * targets remain unknown; no compatibility between runtime forks is inferred.
	 * 
	 * @param target target to assess
	 * @return structural rejection, declared support, or unknown support
	 */
	default Compatibility compatibility(@NonNull Label target) {
		if (!target.distribution().capabilities().contains(getClass())) {
			return Compatibility.UNSUPPORTED;
		}
		if (!labels().stream().map(Label::version)
				.anyMatch((version) -> version.equals(target.version()))) {
			return Compatibility.UNSUPPORTED;
		}
		if (!labels().stream().map(Label::environment)
				.anyMatch((environment) -> environment.equals(target.environment()))) {
			return Compatibility.UNSUPPORTED;
		}
		return labels().stream().anyMatch(label -> label.match(target))
				? Compatibility.SUPPORTED
				: Compatibility.UNKNOWN;
	}

	/**
	 * Check against a given collection's images for image correctness.
	 * 
	 * @param images generated images
	 * @return verified set of images
	 * @throws IllegalArgumentException when generated images cannot be verified
	 */
	static Set<Image<?>> fence(@NonNull Collection<@NonNull ? extends Image<?>> images) throws IllegalArgumentException {
		Map<Path, Image<?>> byPath = new LinkedHashMap<>();

		for (Image<?> image : images) {
			if (!image.path().isAbsolute()) {
				throw new IllegalArgumentException("Images contain a path which must be resolved against a context root");
			}

			Path path = image.path().normalize();
			byPath.putIfAbsent(path, image);
		}

		for (Path path : byPath.keySet()) {
			for (Path parent = path.getParent(); parent != null; parent = parent.getParent()) {
				if (byPath.containsKey(parent)) {
					throw new IllegalArgumentException("A file destination may not also be used as a directory");
				}
			}
		}

		return Set.copyOf(byPath.values());
	}
}
