package cat.tabbie.sdk.minecraft.distribution;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.merchant.Provider;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Package;
import lombok.NonNull;

/**
 * A provider-owned catalog project publishing releases of one distribution,
 * such as Paper's server builds or Fabric's loader. It mirrors
 * {@code Addon} for distributions. Implementations supply immutable, non-null
 * collections, a non-blank name, and builds released under this project's
 * coordinates for this project's distribution; {@link #validate} checks these.
 * Discovery may produce a project without builds.
 */
public interface Product {

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
	 * @return immutable builds with distinct coordinates, possibly empty
	 */
	@NonNull
	Set<Build> builds();

	/**
	 * Exposes the most recent build of this project on any channel.
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
	 * Checks copied constructor parameters before record fields are assigned.
	 *
	 * @param coordinates  candidate project coordinates
	 * @param projectName  candidate display name
	 * @param distribution candidate distribution
	 * @param builds       candidate builds
	 * @throws IllegalArgumentException when the name is blank, a build belongs to
	 *                                  another project or serves another
	 *                                  distribution, or build coordinates repeat
	 */
	static void validate(
			@NonNull Coordinate.Project coordinates,
			@NonNull String projectName,
			@NonNull Distribution distribution,
			@NonNull Set<Build> builds) {
		if (projectName.isBlank()) {
			throw new IllegalArgumentException("A product needs a non-blank name.");
		}
		if (!builds.stream().allMatch(build -> build.project().equals(coordinates))) {
			throw new IllegalArgumentException("Every build must be a release of this product's project.");
		}
		if (!builds.stream().allMatch(build -> build.distribution().equals(distribution))) {
			throw new IllegalArgumentException("Every build must serve this product's distribution.");
		}
		if (builds.stream().map(Build::coordinates).distinct().count() != builds.size()) {
			throw new IllegalArgumentException("Product builds must have distinct coordinates.");
		}
	}

	/**
	 * A published distribution release: its byproducts, all serving one
	 * distribution, and the system packages they require.
	 *
	 * @param coordinates   exact release coordinates
	 * @param releaseName   non-blank display name
	 * @param releaseDate   publication instant
	 * @param releaseNumber positive provider-assigned release number
	 * @param content       nonempty byproducts, each a distinct file of this
	 *                      release
	 * @param packages      system packages the byproducts require
	 */
	record Build(
			@NonNull Coordinate.Build coordinates,
			@NonNull String releaseName,
			@NonNull Instant releaseDate,
			long releaseNumber,
			@NonNull Set<Byproduct> content,
			@NonNull Set<Package> packages) implements Release<Byproduct> {

		/**
		 * Copies the byproducts and packages, then checks shared release
		 * invariants, that every byproduct is a distinct file of this release, and
		 * that they serve one distribution.
		 *
		 * @throws IllegalArgumentException when publication invariants are violated
		 *                                  or byproducts span distributions
		 */
		public Build {
			content = Set.copyOf(content);
			packages = Set.copyOf(packages);
			Release.validate(releaseName, releaseNumber);
			Release.Payload.validate(coordinates, content);

			if (content.stream()
					.flatMap(byproduct -> byproduct.labels().stream())
					.map(Label::distribution)
					.distinct()
					.count() != 1) {
				throw new IllegalArgumentException("A distribution release serves exactly one distribution.");
			}
		}

		/**
		 * Returns the one distribution every byproduct of this release serves.
		 *
		 * @return distribution family value
		 */
		public Distribution distribution() {
			return content.iterator().next().labels().iterator().next().distribution();
		}

		/**
		 * Returns every target some executable of this release serves.
		 *
		 * @return immutable targets, empty when the release has no executable
		 */
		public Set<Label> labels() {
			return content.stream()
					.filter(Byproduct.Executable.class::isInstance)
					.flatMap(byproduct -> byproduct.labels().stream())
					.collect(Collectors.toUnmodifiableSet());
		}
	}
}
