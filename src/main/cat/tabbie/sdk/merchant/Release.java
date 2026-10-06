package cat.tabbie.sdk.merchant;

import java.time.Instant;
import java.util.Comparator;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.platform.Package;
import lombok.NonNull;

/**
 * An immutable, provider-published release of one catalog project. The same
 * contract describes distribution releases (a game version, a loader build, a
 * server build) and addon releases (a mod, plugin, or resource pack version).
 * Target compatibility, installation layout, and dependency resolution belong
 * to the content type or the implementing release, not to this contract.
 *
 * <p>
 * Implementations are expected to be records whose compact constructors copy
 * their collections and then call {@link #validate(String, long)} and
 * {@link Payload#validate(Provider.Coordinate.Build, Set)}.
 *
 * @param <Content> released content item type
 */
public interface Release<Content> {

	/**
	 * Orders releases of the same project by release number, then publication
	 * date. Providers without native numbers synthesize them in publication
	 * order, so equal synthesized numbers fall back to the date. Ordering
	 * releases of different projects is meaningless.
	 */
	Comparator<Release<?>> PUBLICATION_ORDER = Comparator
			.<Release<?>>comparingLong(Release::releaseNumber)
			.thenComparing(Release::releaseDate);

	/**
	 * Returns provider coordinates for this exact release.
	 *
	 * @return release coordinates
	 */
	@NonNull
	Provider.Coordinate.Build coordinates();

	/**
	 * Returns the release display name.
	 *
	 * @return non-blank display name
	 */
	@NonNull
	String releaseName();

	/**
	 * Returns the publication instant.
	 *
	 * @return publication instant
	 */
	@NonNull
	Instant releaseDate();

	/**
	 * Returns the provider-assigned release number, increasing with publication
	 * within a project. Providers without native numbers synthesize one.
	 *
	 * @return positive release number
	 */
	long releaseNumber();

	/**
	 * Returns the released content.
	 *
	 * @return immutable, nonempty content
	 */
	@NonNull
	Set<Content> content();

	/**
	 * Returns the system packages this release requires on the target machine,
	 * such as a Java runtime. Declaring them installs nothing.
	 *
	 * @return immutable packages, empty by default
	 */
	@NonNull
	default Set<Package> packages() {
		return Set.of();
	}

	/**
	 * Returns the provider that published this release.
	 *
	 * @return owning provider identity
	 */
	default Identity<Provider<?, ?>> providerId() {
		return coordinates().providerId();
	}

	/**
	 * Returns the project this release belongs to.
	 *
	 * @return owning project coordinates
	 */
	default Provider.Coordinate.Project project() {
		return coordinates().project();
	}

	/**
	 * Content published as an exact file of a release, such as a mod jar, plugin
	 * jar, resource pack archive, or server jar. Content types that correspond to
	 * provider files implement this so releases can check payload ownership.
	 *
	 * @param <Contextual> accepted installation context
	 */
	interface Payload<Contextual extends Installer.Context> extends Installer<Contextual> {

		/**
		 * Returns provider coordinates for this exact file.
		 *
		 * @return file coordinates
		 */
		@NonNull
		Provider.Coordinate.File coordinates();

		/**
		 * Checks that a release's payloads are each a distinct file of that release.
		 * Callers copy the payloads first; this method does not return a copy.
		 *
		 * @param <P>         payload type
		 * @param coordinates owning release coordinates
		 * @param payloads    candidate payloads
		 * @throws IllegalArgumentException when empty, when a payload belongs to
		 *                                  another release, or when two payloads
		 *                                  share file coordinates
		 * @throws NullPointerException     when the set contains null
		 */
		static <P extends Payload<?>> void validate(
				@NonNull Provider.Coordinate.Build coordinates,
				@NonNull Set<P> payloads) {
			Set<P> copy = Set.copyOf(payloads);
			if (copy.isEmpty()) {
				throw new IllegalArgumentException("A release needs at least one payload.");
			}
			if (copy.stream().anyMatch(payload -> !payload.coordinates().build().equals(coordinates))) {
				throw new IllegalArgumentException("Every payload must be a file of this release.");
			}
			if (copy.stream().map(Payload::coordinates).distinct().count() != copy.size()) {
				throw new IllegalArgumentException("Release payloads must have distinct file coordinates.");
			}
		}

	}

	/**
	 * Checks shared publication invariants. Content is checked separately by
	 * {@link Payload#validate(Provider.Coordinate.Build, Set)}.
	 *
	 * @param releaseName   candidate display name
	 * @param releaseNumber candidate release number
	 * @throws IllegalArgumentException when the name is blank or the number is
	 *                                  not positive
	 */
	static void validate(
			@NonNull String releaseName,
			long releaseNumber) {
		if (releaseName.isBlank() || releaseNumber < 1L) {
			throw new IllegalArgumentException("A release needs a nonblank name and a positive number.");
		}
	}
}