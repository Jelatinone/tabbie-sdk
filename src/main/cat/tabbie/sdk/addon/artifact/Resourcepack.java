package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Intermediate;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Edition-specific resource content. Each edition has its own capability token;
 * support for one does not imply support for the other.
 */
public sealed interface Resourcepack extends Artifact {
	/**
	 * Java resource content. Client placement has a common default; server
	 * resource-pack delivery requires an explicit caller layout.
	 */
	sealed interface Java extends Resourcepack {
		/**
		 * An artifact using the shared named-file and ZIP capture artist.
		 *
		 * @param artifactId   stable catalog artifact identity
		 * @param artifactName nonblank display name
		 * @param store        caller-owned source store
		 * @param labels       nonempty supported targets
		 * @param depends      external dependencies
		 * @param conflicts    external conflicts
		 */
		record Default(
				@NonNull Identity<Artifact> artifactId,
				@NonNull String artifactName,
				@NonNull Store store,
				@NonNull Set<Label> labels,
				@NonNull Set<Identity<Artifact>> depends,
				@NonNull Set<Identity<Artifact>> conflicts) implements Java {
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
				Artifact.validate(Java.class, artifactId, artifactName, store, labels, depends, conflicts);
			}

			@Override
			public Intermediate<Set<Image<?>>> images(@NonNull Context context) throws IOException {
				return Artifact.DEFAULT_ARTIST.paint(this, context);
			}
		}

		/**
		 * An artifact whose content images are described by a caller-supplied artist.
		 *
		 * @param artifactId   stable catalog artifact identity
		 * @param artifactName nonblank display name
		 * @param store        caller-owned source store
		 * @param labels       nonempty supported targets
		 * @param depends      external dependencies
		 * @param conflicts    external conflicts
		 * @param artist       typed content description strategy
		 */
		record Custom(
				@NonNull Identity<Artifact> artifactId,
				@NonNull String artifactName,
				@NonNull Store store,
				@NonNull Set<Label> labels,
				@NonNull Set<Identity<Artifact>> depends,
				@NonNull Set<Identity<Artifact>> conflicts,
				@NonNull Artist<? super Java> artist) implements Java {
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
				Artifact.validate(Java.class, artifactId, artifactName, store, labels, depends, conflicts);
			}

			@Override
			public Intermediate<Set<Image<?>>> images(@NonNull Context context) throws IOException {
				return artist().paint(this, context);
			}
		}
	}

	/**
	 * Bedrock resource content requiring activation metadata at integration time.
	 * Placement is selected explicitly by the caller.
	 */
	sealed interface Bedrock extends Resourcepack {

		/**
		 * An artifact using the shared named-file and ZIP capture artist.
		 *
		 * @param artifactId   stable catalog artifact identity
		 * @param artifactName nonblank display name
		 * @param store        caller-owned source store
		 * @param labels       nonempty supported targets
		 * @param depends      external dependencies
		 * @param conflicts    external conflicts
		 */
		record Default(
				@NonNull Identity<Artifact> artifactId,
				@NonNull String artifactName,
				@NonNull Store store,
				@NonNull Set<Label> labels,
				@NonNull Set<Identity<Artifact>> depends,
				@NonNull Set<Identity<Artifact>> conflicts) implements Bedrock {
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
				Artifact.validate(Bedrock.class, artifactId, artifactName, store, labels, depends, conflicts);
			}

			@Override
			public Intermediate<Set<Image<?>>> images(@NonNull Context context) throws IOException {
				return Artifact.DEFAULT_ARTIST.paint(this, context);
			}
		}

		/**
		 * An artifact whose content images are described by a caller-supplied artist.
		 *
		 * @param artifactId   stable catalog artifact identity
		 * @param artifactName nonblank display name
		 * @param store        caller-owned source store
		 * @param labels       nonempty supported targets
		 * @param depends      external dependencies
		 * @param conflicts    external conflicts
		 * @param artist       typed content description strategy
		 */
		record Custom(
				@NonNull Identity<Artifact> artifactId,
				@NonNull String artifactName,
				@NonNull Store store,
				@NonNull Set<Label> labels,
				@NonNull Set<Identity<Artifact>> depends,
				@NonNull Set<Identity<Artifact>> conflicts,
				@NonNull Artist<? super Bedrock> artist) implements Bedrock {
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
				Artifact.validate(Bedrock.class, artifactId, artifactName, store, labels, depends, conflicts);
			}

			@Override
			public Intermediate<Set<Image<?>>> images(@NonNull Context context) throws IOException {
				return artist().paint(this, context);
			}
		}
	}
}
