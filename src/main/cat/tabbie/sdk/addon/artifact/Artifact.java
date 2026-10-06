package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * Provider-described content and its logical placement. Installers capture
 * bytes from the artifact's source into the context's retention backend and
 * describe changes without writing to the target installation filesystem.
 * Sources and retention backends remain caller-owned. File coordinates
 * identify the exact provider-scoped payload; relations name catalog
 * coordinates at any level, matched by {@link Coordinate#includes(Coordinate)}.
 */
public sealed interface Artifact extends Release.Payload<Artifact.Context>
		permits Mod, Plugin, Datapack, Behaviourpack, Modpack, Resourcepack, Artifact.Custom {

	/**
	 * Stable artifact identity derived from the file coordinates, so equal
	 * coordinates always yield the same identity across processes.
	 *
	 * @return artifact identity
	 */
	default Identity<Artifact> artifactId() {
		return Identity.create(coordinates().canonical());
	}

	/**
	 * Human-readable canonical artifact name
	 *
	 * @return artifact name
	 */
	@NonNull
	String artifactName();

	/**
	 * Provider-owned description of this artifact's content, captured during
	 * installation.
	 *
	 * @return content source
	 */
	@NonNull
	Describe source();

	/**
	 * Declares support for the entire artifact.
	 *
	 * @return immutable, nonempty supported targets
	 */
	@NonNull
	Set<Label> labels();

	/**
	 * Declares which multiplayer sides this artifact runs on and what it
	 * requires of the other side.
	 *
	 * @return side parity, {@link Parity#UNKNOWN} without evidence
	 */
	@NonNull
	Parity parity();

	/**
	 * Catalog relationships with other content, at most one per coordinate.
	 *
	 * @return immutable relations
	 */
	@NonNull
	Set<Relation> relations();

	/**
	 * Catalog content that must be installed alongside this artifact. A project
	 * coordinate is satisfied by any file of that project, a build coordinate by
	 * any file of that release, and a file coordinate only by that exact file.
	 *
	 * @return required coordinates
	 */
	default Set<Coordinate> depends() {
		return coordinates(Relation.Required.class);
	}

	/**
	 * Catalog content that may not be installed alongside this artifact, matched
	 * the same way as {@link #depends()}.
	 *
	 * @return conflicting coordinates
	 */
	default Set<Coordinate> conflicts() {
		return coordinates(Relation.Incompatible.class);
	}

	/**
	 * Selects the coordinates of one kind of relation.
	 *
	 * @param kind relation kind, such as {@code Relation.Required.class}
	 * @return immutable coordinates of that kind
	 */
	default Set<Coordinate> coordinates(@NonNull Class<? extends Relation> kind) {
		return relations().stream()
				.filter(relation -> relation.getClass() == kind)
				.map(Relation::coordinate)
				.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Captures the source into the context's retention backend and places it at
	 * the selected layout: as one named file, or unpacked beneath the layout
	 * directory. Nothing is read until the result is collapsed.
	 *
	 * @param context selected target
	 * @return unevaluated capture producing validated immutable images
	 * @throws IOException              when no layout can be determined
	 * @throws IllegalArgumentException when the target is not explicitly
	 *                                  supported
	 */
	@Override
	default Intermediate<Set<Image<?>>> install(@NonNull Context context) throws IOException {
		Layout layout = context.layout(this);
		Extract repository = context.repository();
		return repository.capture(source())
				.flatMap(captured -> layout.unpack()
						? context.archive().unpack(repository.retained(captured), repository, layout.relative())
						: Intermediate.of(Set.<Image<?>>of(
								Image.create(layout.relative().resolve(captured.fileName()), captured))))
				.map(Artifact::checked);
	}

	/**
	 * Checks copied constructor parameters before record fields are assigned.
	 *
	 * @param artifactFamily artifact family token
	 * @param coordinates    exact file coordinates
	 * @param artifactName   display name
	 * @param labels         declared targets
	 * @param relations      catalog relationships
	 *
	 * @throws IllegalArgumentException when declarations contradict structural
	 *                                  facts, a relation includes this artifact,
	 *                                  a coordinate has two relations, or an
	 *                                  incompatibility includes another relation
	 */
	static void validate(
			@NonNull Class<? extends Artifact> artifactFamily,
			@NonNull Coordinate.File coordinates,
			@NonNull String artifactName,
			@NonNull Set<Label> labels,
			@NonNull Set<Relation> relations) {
		if (artifactName.isBlank() || labels.isEmpty()) {
			throw new IllegalArgumentException("An artifact needs a non-blank name and supported labels.");
		}
		if (labels.stream().anyMatch(label -> !label.distribution().supports(artifactFamily))) {
			throw new IllegalArgumentException("A declared distribution does not support this artifact family.");
		}
		if (relations.stream().anyMatch(relation -> relation.coordinate().includes(coordinates))) {
			throw new IllegalArgumentException("Relations must not reference this artifact.");
		}
		if (relations.stream().map(Relation::coordinate).distinct().count() != relations.size()) {
			throw new IllegalArgumentException("A coordinate may have only one relation.");
		}
		if (relations.stream()
				.filter(relation -> relation instanceof Relation.Incompatible)
				.anyMatch(conflict -> relations.stream()
						.filter(other -> !(other instanceof Relation.Incompatible))
						.anyMatch(other -> conflict.coordinate().includes(other.coordinate())))) {
			throw new IllegalArgumentException("An incompatibility must not include another relation.");
		}
	}

	/**
	 * Requires an explicitly supported target before invoking an installer.
	 *
	 * @param context selected target
	 * @throws IllegalArgumentException when the target is not explicitly
	 *                                  supported
	 */
	default void require(@NonNull Context context) {
		if (context.label().compatibility(this) != Compatibility.SUPPORTED) {
			throw new IllegalArgumentException("Image generation requires explicit target support.");
		}
	}

	/**
	 * Checks installer output and returns an immutable copy.
	 *
	 * @param images generated images
	 * @return validated immutable images
	 */
	private static Set<Image<?>> checked(Set<Image<?>> images) {
		Image.validate(images);
		return Set.copyOf(images);
	}

	/**
	 * An artifact whose images are described by a caller-supplied installer. The
	 * same target support and image checks as the shared installer apply.
	 */
	sealed interface Custom extends Artifact
			permits Mod.Custom, Plugin.Custom, Datapack.Custom, Behaviourpack.Custom, Modpack.Custom,
			Resourcepack.Java.Custom, Resourcepack.Bedrock.Custom {

		/**
		 * Returns the caller-supplied installer.
		 *
		 * @return typed content description strategy
		 */
		@NonNull
		Installer<Context> installer();

		/**
		 * Requires explicit target support, then delegates to the caller-supplied
		 * installer and validates its images.
		 *
		 * @param context selected target
		 * @return unevaluated capture producing validated immutable images
		 * @throws IOException              when the installer fails to describe
		 *                                  the content
		 * @throws IllegalArgumentException when the target is not explicitly
		 *                                  supported
		 */
		@Override
		default Intermediate<Set<Image<?>>> install(@NonNull Context context) throws IOException {
			require(context);
			return installer().install(context).map(Artifact::checked);
		}
	}

	/**
	 * A catalog relationship with other content, matched by
	 * {@link Coordinate#includes(Coordinate)}. The set of relation kinds is
	 * closed so that every kind has a defined meaning during resolution.
	 */
	sealed interface Relation permits Relation.Required, Relation.Optional, Relation.Incompatible, Relation.Embedded {

		/**
		 * Returns the related catalog coordinate.
		 *
		 * @return related coordinate at any level
		 */
		@NonNull
		Coordinate coordinate();

		/**
		 * Content that must be installed alongside.
		 *
		 * @param coordinate required coordinate
		 */
		record Required(@NonNull Coordinate coordinate) implements Relation {
		}

		/**
		 * Content that enhances this artifact when present.
		 *
		 * @param coordinate optional coordinate
		 */
		record Optional(@NonNull Coordinate coordinate) implements Relation {
		}

		/**
		 * Content that may not be installed alongside.
		 *
		 * @param coordinate incompatible coordinate
		 */
		record Incompatible(@NonNull Coordinate coordinate) implements Relation {
		}

		/**
		 * Content already bundled inside this artifact.
		 *
		 * @param coordinate embedded coordinate
		 */
		record Embedded(@NonNull Coordinate coordinate) implements Relation {
		}

	}

	/**
	 * Selected target, logical mounts, retention, and per-artifact layout
	 * overrides, independent of physical paths.
	 */
	interface Context extends Installer.Context {

		/**
		 * Returns per-artifact layout overrides.
		 *
		 * @return per-artifact layout overrides
		 */
		default Map<Identity<Artifact>, Layout> layouts() {
			return Map.of();
		}

		/**
		 * Returns the limits applied when unpacking archives.
		 *
		 * @return archive limits, {@link Archive#DEFAULT} by default
		 */
		@NonNull
		default Archive archive() {
			return Archive.DEFAULT;
		}

		/**
		 * Selects an override or the distribution's default layout.
		 *
		 * @param artifact selected artifact
		 * @return scoped logical layout
		 * @throws IOException              when no default or override exists
		 * @throws IllegalArgumentException when the target is not explicitly
		 *                                  supported
		 */
		default Layout layout(@NonNull Artifact artifact) throws IOException {
			artifact.require(this);
			Layout override = layouts().get(artifact.artifactId());
			return override != null
					? override
					: label().distribution().layout(artifact, this);
		}

		/**
		 * Immutable context with explicit mounts and layout overrides.
		 *
		 * @param label       selected target
		 * @param platform    target platform
		 * @param repository  caller-owned retention backend
		 * @param contextRoot context mount relative to the installation
		 * @param worldRoot   selected world relative to the context mount
		 * @param layouts     per-artifact layout overrides
		 */
		record Default(
				@NonNull Label label,
				@NonNull Platform platform,
				@NonNull Extract repository,
				@NonNull Relative.Root contextRoot,
				@NonNull Relative.World worldRoot,
				@NonNull Map<Identity<Artifact>, Layout> layouts) implements Context {

			/**
			 * Copies the layout overrides.
			 */
			public Default {
				layouts = Map.copyOf(layouts);
			}

			/**
			 * Creates a context without layout overrides.
			 *
			 * @param label       selected target
			 * @param platform    target platform
			 * @param repository  caller-owned retention backend
			 * @param contextRoot context mount relative to the installation
			 * @param worldRoot   selected world relative to the context mount
			 */
			public Default(@NonNull Label label, @NonNull Platform platform, @NonNull Extract repository,
					@NonNull Relative.Root contextRoot, @NonNull Relative.World worldRoot) {
				this(label, platform, repository, contextRoot, worldRoot, Map.of());
			}
		}
	}

	/**
	 * Scoped placement. A named-file layout names the directory receiving the
	 * file; an unpacking layout names the exact extraction root.
	 *
	 * @param unpack   whether the content is a ZIP archive to extract
	 * @param relative scoped destination directory
	 */
	record Layout(boolean unpack, @NonNull Relative relative) {
	}
}
