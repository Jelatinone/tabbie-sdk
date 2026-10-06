package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import lombok.NonNull;

/**
 * Bedrock resource content. By default each pack unpacks beneath its own
 * directory. Placing the files does not activate the pack: the world's
 * {@code world_resource_packs.json} must also list its manifest UUID and
 * version, which this SDK does not yet describe, so Bedrock packs are not
 * yet installable end to end.
 */
public sealed interface Resourcepack extends Artifact {

	/**
	 * Java resource content. Client placement has a common default; server
	 * resource-pack delivery requires an explicit caller layout.
	 */
	sealed interface Java extends Resourcepack {

		/**
		 * An artifact using the shared named-file and ZIP capture installer.
		 *
		 * @param coordinates  exact provider file coordinates
		 * @param artifactName non-blank display name
		 * @param source       provider-owned content description
		 * @param labels       nonempty supported targets
		 * @param parity       multiplayer side parity
		 * @param relations    catalog relationships, one per coordinate
		 */
		record Default(
				@NonNull Coordinate.File coordinates,
				@NonNull String artifactName,
				@NonNull Describe source,
				@NonNull Set<Label> labels,
				@NonNull Parity parity,
				@NonNull Set<Relation> relations) implements Java {
			/**
			 * Copies collections and checks local declarations without reading content.
			 *
			 * @throws IllegalArgumentException when declared support or relations are
			 *                                  invalid
			 */
			public Default {
				labels = Set.copyOf(labels);
				relations = Set.copyOf(relations);
				Artifact.validate(Java.class, coordinates, artifactName, labels, relations);
			}
		}

		/**
		 * An artifact whose content images are described by a caller-supplied
		 * installer, checked like the shared installer's.
		 *
		 * @param coordinates  exact provider file coordinates
		 * @param artifactName non-blank display name
		 * @param source       provider-owned content description
		 * @param labels       nonempty supported targets
		 * @param parity       multiplayer side parity
		 * @param relations    catalog relationships, one per coordinate
		 * @param installer    typed content description strategy
		 */
		record Custom(
				@NonNull Coordinate.File coordinates,
				@NonNull String artifactName,
				@NonNull Describe source,
				@NonNull Set<Label> labels,
				@NonNull Parity parity,
				@NonNull Set<Relation> relations,
				@NonNull Installer<Artifact.Context> installer) implements Java, Artifact.Custom {
			/**
			 * Copies collections and checks local declarations without reading content.
			 *
			 * @throws IllegalArgumentException when declared support or relations are
			 *                                  invalid
			 */
			public Custom {
				labels = Set.copyOf(labels);
				relations = Set.copyOf(relations);
				Artifact.validate(Java.class, coordinates, artifactName, labels, relations);
			}
		}
	}

	/**
	 * Bedrock resource content requiring activation metadata at integration time.
	 * By default each pack unpacks beneath its own directory.
	 */
	sealed interface Bedrock extends Resourcepack {

		/**
		 * An artifact using the shared named-file and ZIP capture installer.
		 *
		 * @param coordinates  exact provider file coordinates
		 * @param artifactName non-blank display name
		 * @param source       provider-owned content description
		 * @param labels       nonempty supported targets
		 * @param parity       multiplayer side parity
		 * @param relations    catalog relationships, one per coordinate
		 */
		record Default(
				@NonNull Coordinate.File coordinates,
				@NonNull String artifactName,
				@NonNull Describe source,
				@NonNull Set<Label> labels,
				@NonNull Parity parity,
				@NonNull Set<Relation> relations) implements Bedrock {
			/**
			 * Copies collections and checks local declarations without reading content.
			 *
			 * @throws IllegalArgumentException when declared support or relations are
			 *                                  invalid
			 */
			public Default {
				labels = Set.copyOf(labels);
				relations = Set.copyOf(relations);
				Artifact.validate(Bedrock.class, coordinates, artifactName, labels, relations);
			}
		}

		/**
		 * An artifact whose content images are described by a caller-supplied
		 * installer, checked like the shared installer's.
		 *
		 * @param coordinates  exact provider file coordinates
		 * @param artifactName non-blank display name
		 * @param source       provider-owned content description
		 * @param labels       nonempty supported targets
		 * @param parity       multiplayer side parity
		 * @param relations    catalog relationships, one per coordinate
		 * @param installer    typed content description strategy
		 */
		record Custom(
				@NonNull Coordinate.File coordinates,
				@NonNull String artifactName,
				@NonNull Describe source,
				@NonNull Set<Label> labels,
				@NonNull Parity parity,
				@NonNull Set<Relation> relations,
				@NonNull Installer<Artifact.Context> installer) implements Bedrock, Artifact.Custom {
			/**
			 * Copies collections and checks local declarations without reading content.
			 *
			 * @throws IllegalArgumentException when declared support or relations are
			 *                                  invalid
			 */
			public Custom {
				labels = Set.copyOf(labels);
				relations = Set.copyOf(relations);
				Artifact.validate(Bedrock.class, coordinates, artifactName, labels, relations);
			}
		}
	}
}
