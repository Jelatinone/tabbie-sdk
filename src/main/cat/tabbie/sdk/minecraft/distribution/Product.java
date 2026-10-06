package cat.tabbie.sdk.minecraft.distribution;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.merchant.Provider;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * A published distribution release of one instance, run for every target the
 * instance declares in {@link Instance#labels()}.
 * A provider-owned catalog project publishing releases of one distribution,
 * such as Paper's server builds or Fabric's loader. Implementations supply a
 * non-blank name and builds released under this project's coordinates;
 * discovery may produce a project without builds.
 */
interface Product {

	/**
	 * Returns provider coordinates for this project.
	 *
	 * @return project coordinates
	 */
	@NonNull
	Provider.Coordinate.Project coordinates();

	/**
	 * Names this project for display.
	 *
	 * @return non-blank name
	 */
	@NonNull
	String projectName();

	/**
	 * Returns the distribution every release of this project runs as.
	 *
	 * @return distribution family value
	 */
	@NonNull
	Distribution distribution();

	/**
	 * Exposes the known releases of this project.
	 *
	 * @return immutable builds, possibly empty
	 */
	@NonNull
	Set<Build> builds();

	/**
	 * Exposes the most recent build owned by this addon on any channel.
	 *
	 * @return latest build, or empty when no build is known
	 */
	@NonNull
	default Optional<Build> latest() {
		return builds().stream()
				.max(Release.PUBLICATION_ORDER);
	}

	/**
	 * Selects the most recent release on an accepted channel.
	 *
	 * @param channels accepted channels
	 * @return latest accepted build, or empty when none is known
	 */
	@NonNull
	default Optional<Build> latest(@NonNull Set<Provider.Coordinate.Channel> channels) {
		return builds().stream()
				.filter(build -> channels.contains(build.coordinates().channel()))
				.max(Release.PUBLICATION_ORDER);
	}

	/**
	 * A published distribution release of one instance, run for every target the
	 * instance declares in {@link Instance#labels()}.
	 *
	 * @param coordinates   exact release coordinates
	 * @param releaseName   non-blank display name
	 * @param releaseDate   publication instant
	 * @param releaseNumber positive provider-assigned release number
	 * @param content       instance published as a file of this release
	 * @param packages      system packages the instance requires
	 */
	record Build(
			@NonNull Coordinate.Build coordinates,
			@NonNull String releaseName,
			@NonNull Instant releaseDate,
			long releaseNumber,
			@NonNull Set<Byproduct> content,
			@NonNull Set<Package> packages) implements Release<Byproduct> {

		/**
		 * Checks shared release invariants and that the instance is a file of this
		 * release, then copies the packages.
		 *
		 * @throws IllegalArgumentException when publication invariants are violated
		 */
		public Build {
			content = Set.copyOf(content);
			packages = Set.copyOf(packages);
			Release.validate(releaseName, releaseNumber);
			Release.Payload.validate(coordinates, content);

			if (content.stream().flatMap(byproduct -> byproduct.labels().stream()).map(Label::distribution).distinct()
					.count() != 1) {
				throw new IllegalArgumentException("A distribution release serves exactly one distribution.");
			}
		}

		/**
		 * Returns every target some executable of this release serves.
		 *
		 * @return immutable targets
		 */
		public Set<Label> labels() {
			return content.stream()
					.filter(Byproduct.Executable.class::isInstance)
					.flatMap(byproduct -> byproduct.labels().stream())
					.collect(Collectors.toUnmodifiableSet());
		}
	}
}
