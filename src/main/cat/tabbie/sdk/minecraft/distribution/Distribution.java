package cat.tabbie.sdk.minecraft.distribution;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Behaviourpack;
import cat.tabbie.sdk.addon.artifact.Datapack;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.addon.artifact.Modpack;
import cat.tabbie.sdk.addon.artifact.Plugin;
import cat.tabbie.sdk.addon.artifact.Resourcepack;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Release;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Version;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * A stateless runtime family describing edition, environments, capabilities,
 * and common content placement. Version resolution, installation conflicts,
 * filesystem observations, and deployment belong to Core.
 *
 * The public values are the {@link Java.Of} and {@link Bedrock.Of} constants,
 * listed by {@link #values()} and found by {@link #of(String)}; equality is
 * identity.
 *
 */
public sealed interface Distribution permits Java, Bedrock {

	/**
	 * Enumerates every canonical distribution value.
	 *
	 * @return immutable distributions, Java before Bedrock, in declaration order
	 */
	static List<Distribution> values() {
		return Stream.<Distribution>concat(
				Java.values().stream(),
				Bedrock.values().stream())
				.toList();
	}

	/**
	 * Looks up a canonical distribution by its exact namespaced identifier, for
	 * example when reading a persisted lock.
	 *
	 * @param id case-sensitive identifier such as {@code java:fabric}
	 * @return matching distribution, or empty when unknown
	 */
	static Optional<Distribution> of(@NonNull String id) {
		return values().stream()
				.filter(distribution -> distribution.id().equals(id))
				.findFirst();
	}

	/**
	 * Checks that an identifier belongs to the Bedrock or Java namespaces, so a
	 * mistyped constant fails when its enum loads rather than when a persisted lock
	 * is read.
	 *
	 * @param id candidate identifier
	 * @return the identifier
	 * @throws IllegalArgumentException when it is not {@code bedrock:} or
	 *                                  {@code java:} followed
	 *                                  by lowercase letters and digits
	 */
	static void validate(@NonNull String id) {
		if (!id.matches("(bedrock|java):[a-z0-9]+(-[a-z0-9]+)*")) {
			throw new IllegalArgumentException(
					String.format("%s is not a distribution identifier.", id));
		}
	}

	/**
	 * Returns a stable, case-sensitive namespaced identifier.
	 *
	 * @return distribution identifier
	 */
	String id();

	/**
	 * Returns supported physical runtime environments.
	 *
	 * @return immutable environments
	 */
	Set<Environment> environments();

	/**
	 * Returns supported artifact-family class tokens.
	 *
	 * @return immutable capabilities
	 */
	Set<Class<? extends Artifact>> capabilities();

	/**
	 * Tests edition membership without discovering a runtime release.
	 *
	 * @param version discovered game version
	 * @return whether the version belongs to the supported edition
	 */
	boolean applicable(@NonNull Version version);

	/**
	 * Tests a family interface or concrete artifact against family capabilities.
	 *
	 * @param family family or implementation class
	 * @return whether a declared capability is assignable from the supplied type
	 */
	default boolean supports(@NonNull Class<? extends Artifact> family) {
		return capabilities().stream().anyMatch(capability -> capability.isAssignableFrom(family));
	}

	/**
	 * Determines the default layout of an artifact on this distribution. Each
	 * unpacked pack receives its own directory named by its artifact identity, so
	 * two packs never collide. Content without a meaningful default, such as
	 * server resource-pack delivery or modpacks on distributions without mod
	 * loading, requires a caller layout override. This method does not discover
	 * installation directories.
	 *
	 * @param artifact reference artifact
	 * @param context  reference context
	 *
	 * @return scoped default layout
	 *
	 * @throws IOException              when no default layout exists
	 * @throws IllegalArgumentException when this distribution does not support the
	 *                                  environment or artifact type
	 */
	@SuppressWarnings("unused")
	default Artifact.Layout layout(@NonNull Artifact artifact, @NonNull Artifact.Context context) throws IOException {
		Environment environment = context.label().environment();
		if (!environments().contains(environment)) {
			throw new IllegalArgumentException(String.format(
					"Distribution %s does not support the %s environment", id(), environment));
		}
		if (!supports(artifact.getClass())) {
			throw new IllegalArgumentException(String.format(
					"Distribution %s does not support %s artifacts", id(), artifact.getClass().getSimpleName()));
		}
		String directory = artifact.artifactId().id().toString();

		return switch (artifact) {
			// Mod (.jar) -> mods/
			case Mod mod ->
				new Artifact.Layout(
						false,
						Relative.root("mods"));

			// Plugin (.jar) -> plugins/
			case Plugin plugin ->
				new Artifact.Layout(
						false,
						Relative.root("plugins"));

			// Resource pack (.zip) -> resourcepacks/ on clients; servers deliver packs
			case Resourcepack.Java resourcepack ->
				new Artifact.Layout(
						false,
						Relative.root("resourcepacks"));

			// Resource pack (.mcpack) -> resource_packs/<id>/
			case Resourcepack.Bedrock resourcepack ->
				new Artifact.Layout(
						true,
						Relative.root("resource_packs", directory));

			// Datapack (.zip) -> <world>/datapacks/<id>/
			case Datapack datapack -> new Artifact.Layout(
					true,
					Relative.world("datapacks", directory));

			// Behaviour pack (.mcpack) -> <world>/behavior_packs/<id>/
			case Behaviourpack behaviourpack ->
				new Artifact.Layout(
						true,
						Relative.world("behavior_packs", directory));

			// Self-contained modpack (.zip) -> context root, on mod loaders only
			case Modpack modpack ->
				new Artifact.Layout(
						true,
						Relative.root("mods"));

		};
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
			Release.validate(releaseName, releaseNumber);
			Release.Payload.validate(coordinates, content);
			packages = Set.copyOf(packages);
		}
	}
}
