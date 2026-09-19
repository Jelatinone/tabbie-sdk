package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Reference;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Self-contained pack content owned by one artifact, including every extracted
 * file. Embedded mods are not separate artifacts or external dependencies.
 * Java loaders default to ZIP extraction beneath their mods directory; explicit
 * layouts may choose another directory or keep the original archive intact.
 * Client and server variants are represented by separate labeled artifacts.
 */
public sealed interface Modpack extends Artifact {
	/**
	 * An artifact using the shared named-file and ZIP capture artist.
	 *
	 * @param artifactId        stable catalog artifact identity
	 * @param artifactName      nonblank display name
	 * @param artifactReference named source content
	 * @param store             caller-owned source store
	 * @param labels            nonempty supported targets
	 * @param depends           external dependencies
	 * @param conflicts         external conflicts
	 */
	record Default(
			@NonNull Identity<Artifact> artifactId,
			@NonNull String artifactName,
			@NonNull Reference artifactReference,
			@NonNull Store store,
			@NonNull Set<Label> labels,
			@NonNull Set<Identity<Artifact>> depends,
			@NonNull Set<Identity<Artifact>> conflicts) implements Modpack {
		/**
		 * Copies collections and checks local declarations without reading content.
		 *
		 * @throws IllegalArgumentException when declared support or relationships are
		 *                                  invalid
		 */
		public Default {
			labels = Set.copyOf(labels);
			depends = Set.copyOf(depends);
			conflicts = Set.copyOf(conflicts);
			validate();
		}

		@Override
		public Set<Image<?>> images(@NonNull Context context) throws IOException {
			return Image.fence(Artifact.DEFAULT_ARTIST.paint(this, context));
		}
	}

	/**
	 * An artifact whose content images are described by a caller-supplied artist.
	 *
	 * @param artifactId        stable catalog artifact identity
	 * @param artifactName      nonblank display name
	 * @param artifactReference named source content
	 * @param store             caller-owned source store
	 * @param labels            nonempty supported targets
	 * @param depends           external dependencies
	 * @param conflicts         external conflicts
	 * @param artist            typed content description strategy
	 */
	record Custom(
			@NonNull Identity<Artifact> artifactId,
			@NonNull String artifactName,
			@NonNull Reference artifactReference,
			@NonNull Store store,
			@NonNull Set<Label> labels,
			@NonNull Set<Identity<Artifact>> depends,
			@NonNull Set<Identity<Artifact>> conflicts,
			@NonNull Artist<? super Modpack> artist) implements Modpack {
		/**
		 * Copies collections and checks local declarations without reading content.
		 *
		 * @throws IllegalArgumentException when declared support or relationships are
		 *                                  invalid
		 */
		public Custom {
			labels = Set.copyOf(labels);
			depends = Set.copyOf(depends);
			conflicts = Set.copyOf(conflicts);
			validate();
		}

		@Override
		public Set<Image<?>> images(@NonNull Context context) throws IOException {
			return Image.fence(Artifact.DEFAULT_ARTIST.paint(this, context));
		}
	}
}