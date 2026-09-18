package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Reference;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

public sealed interface Mod extends Artifact {

	record Default(
			Identity<Artifact> artifactId,
			String artifactName,
			Reference artifactReference,

			Store store,

			Set<Label> labels,

			Set<Identity<Artifact>> depends,
			Set<Identity<Artifact>> conflicts

	) implements Mod {

		public Default {
			labels = Set.copyOf(labels);
			depends = Set.copyOf(depends);
			conflicts = Set.copyOf(conflicts);
		}

		@Override
		public @NonNull Set<Image<?>> images(Context context) throws IOException {
			return Artifact.fence(Artifact.DEFAULT.paint(this, context));
		}
	}

	record Custom(
			Identity<Artifact> artifactId,
			String artifactName,
			Reference artifactReference,

			Store store,

			Set<Label> labels,

			Set<Identity<Artifact>> depends,
			Set<Identity<Artifact>> conflicts,

			@NonNull Artist<Mod> artist

	) implements Mod {

		public Custom {
			labels = Set.copyOf(labels);
			depends = Set.copyOf(depends);
			conflicts = Set.copyOf(conflicts);
		}

		@Override
		public @NonNull Set<Image<?>> images(Context context) throws IOException {
			return Artifact.fence(artist.paint(this, context));
		}
	}

}
