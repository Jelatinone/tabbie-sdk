package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import lombok.NonNull;

/**
 * Self-contained pack content owned by one artifact, including every extracted
 * file. Embedded mods are not separate artifacts or external dependencies. Java
 * loaders default to ZIP extraction at the context root; other distributions
 * require an explicit layout, which may also keep the original archive intact.
 * 
 * Client and server variants are published as separate target-specific builds,
 * because every artifact of a build installs on every target it advertises.
 * Manifest formats that download their contents, such as Modrinth's or
 * CurseForge's, need an importer rather than this artifact.
 */
public sealed interface Modpack extends Artifact {

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
			@NonNull Set<Relation> relations) implements Modpack {
		/**
		 * Copies collections and checks local declarations without reading content.
		 *
		 * @throws IllegalArgumentException when declared support or relations are
		 *                                  invalid
		 */
		public Default {
			labels = Set.copyOf(labels);
			relations = Set.copyOf(relations);
			Artifact.validate(Modpack.class, coordinates, artifactName, labels, relations);
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
			@NonNull Installer<Artifact.Context> installer) implements Modpack, Artifact.Custom {
		/**
		 * Copies collections and checks local declarations without reading content.
		 *
		 * @throws IllegalArgumentException when declared support or relations are
		 *                                  invalid
		 */
		public Custom {
			labels = Set.copyOf(labels);
			relations = Set.copyOf(relations);
			Artifact.validate(Modpack.class, coordinates, artifactName, labels, relations);
		}
	}
}
