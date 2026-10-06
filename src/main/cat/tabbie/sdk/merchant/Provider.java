package cat.tabbie.sdk.merchant;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Criteria;
import cat.tabbie.sdk.api.Query;
import cat.tabbie.sdk.api.Queryable;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * Catalog discovery capability, independent of transport, storage, and content
 * kind. The same contract serves distribution providers (game, loader, and
 * server builds) and addon providers (mods, plugins, resource packs, and
 * similar content). Providers may describe remote, local, cached, or generated
 * content. Implementations return validated immutable declarations and
 * distinguish failed discovery from absent results.
 *
 * <p>
 * Every catalog item a provider publishes carries {@link Coordinate} minted
 * by that provider, so a later process can ask the same provider for the same
 * project, release, or file without shared state.
 *
 * <p>
 * Core queries every provider with the standard {@link Search} criteria.
 * Querying the provider itself finds projects: by coordinate identifier, by
 * text, or by target and family filters. Querying {@link #releases()} finds
 * releases: a project identifier lists that project's releases newest first,
 * a build identifier selects that release, and a file identifier selects the
 * release containing that file. Providers may accept their own {@link Search}
 * implementations with extra criteria.
 *
 * @param <Result>    discovered project type, such as an addon
 * @param <Published> published release type, such as an addon build
 */
public interface Provider<Result, Published extends Release<?>> extends Queryable<Provider.Search, Result> {

	/**
	 * Stable provider identity, also carried by every coordinate this provider
	 * mints.
	 *
	 * @return provider identity
	 */
	@NonNull
	Identity<Provider<?, ?>> providerId();

	/**
	 * Human-readable canonical provider name.
	 *
	 * @return provider name
	 */
	@NonNull
	String providerName();

	/**
	 * Queries releases of this provider's projects.
	 *
	 * @return release queries using the standard criteria
	 */
	@NonNull
	Queryable<Search, Published> releases();

	/**
	 * Resolves project coordinates, for example from a lockfile.
	 *
	 * @param coordinates project coordinates minted by this provider
	 * @return the project, or empty when it no longer exists
	 */
	default Intermediate<Optional<Result>> resolve(@NonNull Coordinate.Project coordinates) {
		return Intermediate.of(ignored -> query(new Query.Singular<>(Search.of(coordinates))));
	}

	/**
	 * Resolves an exact release.
	 *
	 * @param coordinates release coordinates minted by this provider
	 * @return the release, or empty when it no longer exists
	 */
	default Intermediate<Optional<Published>> resolve(@NonNull Coordinate.Build coordinates) {
		return Intermediate.of(ignored -> releases().query(new Query.Singular<>(Search.of(coordinates))));
	}

	/**
	 * Resolves the release containing an exact file.
	 *
	 * @param coordinates file coordinates minted by this provider
	 * @return the containing release, or empty when it no longer exists
	 */
	default Intermediate<Optional<Published>> resolve(@NonNull Coordinate.File coordinates) {
		return Intermediate.of(ignored -> releases().query(new Query.Singular<>(Search.of(coordinates))));
	}

	/**
	 * Lists a page of a project's releases declaring any of the targets, newest
	 * first.
	 *
	 * @param project project coordinates minted by this provider
	 * @param targets targets to filter by, empty for every release
	 * @param limit   positive maximum release count
	 * @return matching releases
	 */
	default Intermediate<Collection<Published>> builds(@NonNull Coordinate.Project project, @NonNull Set<Label> targets,
			int limit) {
		return Intermediate.of(new Query.Several<Search>(Search.of(project).withTargets(targets), limit))
				.map(q -> releases().query(q).orElseThrow());
	}

	/**
	 * Mints project coordinates owned by this provider. Implementations should
	 * create all coordinates through this method and the navigation methods on
	 * its result, so every published item carries this provider's identity.
	 *
	 * @param projectKey opaque provider project key
	 * @return project coordinates owned by this provider
	 * @throws IllegalArgumentException when the key is blank
	 */
	default Coordinate.Project project(@NonNull String projectKey) {
		return new Coordinate.Project(providerId(), projectKey);
	}

	/**
	 * Checks whether coordinates were minted by this provider, for example before
	 * resolving coordinates read back from a lockfile.
	 *
	 * @param coordinates candidate coordinates
	 * @return whether this provider owns the coordinates
	 */
	default boolean owns(@NonNull Coordinate coordinates) {
		return providerId().equals(coordinates.providerId());
	}

	/**
	 * Standard catalog criteria every provider accepts. Absent or empty criteria
	 * do not filter.
	 */
	interface Search extends Criteria<Coordinate> {

		/**
		 * Returns free text to search for.
		 *
		 * @return search text
		 */
		default Optional<String> text() {
			return Optional.empty();
		}

		/**
		 * Returns targets of which a match must declare at least one.
		 *
		 * @return target filter, empty for any
		 */
		default Set<Label> targets() {
			return Set.of();
		}

		/**
		 * Returns artifact families of which a match must publish at least one.
		 *
		 * @return family filter, empty for any
		 */
		default Set<Class<? extends Artifact>> families() {
			return Set.of();
		}

		/**
		 * Selects catalog content by coordinates.
		 *
		 * @param coordinates project, release, or file coordinates
		 * @return criteria with only the identifier
		 */
		static Default of(@NonNull Coordinate coordinates) {
			return new Default(Optional.of(coordinates), Optional.empty(), Set.of(), Set.of(), Optional.empty());
		}

		/**
		 * Searches catalog content by free text.
		 *
		 * @param text search text
		 * @return criteria with only the text
		 */
		static Default text(@NonNull String text) {
			return new Default(Optional.empty(), Optional.of(text), Set.of(), Set.of(), Optional.empty());
		}

		/**
		 * Default immutable search criteria.
		 *
		 * @param identifier direct coordinates
		 * @param text       search text
		 * @param targets    target filter
		 * @param families   artifact family filter
		 * @param duration   provider-interpreted duration
		 */
		record Default(
				@NonNull Optional<Coordinate> identifier,
				@NonNull Optional<String> text,
				@NonNull Set<Label> targets,
				@NonNull Set<Class<? extends Artifact>> families,
				@NonNull Optional<Duration> duration) implements Search {

			/**
			 * Copies the filters and rejects blank text.
			 */
			public Default {
				targets = Set.copyOf(targets);
				families = Set.copyOf(families);
				if (text.isPresent() && text.get().isBlank()) {
					throw new IllegalArgumentException("Search text must be non-blank when present.");
				}
			}

			/**
			 * Replaces the target filter.
			 *
			 * @param targets targets of which a match must declare one
			 * @return updated criteria
			 */
			public Default withTargets(@NonNull Set<Label> targets) {
				return new Default(identifier, text, targets, families, duration);
			}

			/**
			 * Replaces the family filter.
			 *
			 * @param families families of which a match must publish one
			 * @return updated criteria
			 */
			public Default withFamilies(@NonNull Set<Class<? extends Artifact>> families) {
				return new Default(identifier, text, targets, families, duration);
			}

			/**
			 * Replaces the provider-interpreted duration.
			 *
			 * @param duration duration
			 * @return updated criteria
			 */
			public Default withDuration(@NonNull Duration duration) {
				return new Default(identifier, text, targets, families, Optional.of(duration));
			}
		}
	}

	/**
	 * Provider-scoped catalog coordinates. These values carry enough provider
	 * information to query the same project, release, or file again. They contain
	 * no credentials, provider instances, or transport assumptions. Keys are
	 * opaque to everything except the owning provider.
	 *
	 * <p>
	 * Typical mappings:
	 * <ul>
	 * <li>Mojang: project {@code minecraft}, build {@code 1.21.1}, file
	 * {@code client}</li>
	 * <li>Paper: project {@code paper}, build {@code 1.21.1-130}, file
	 * {@code server}</li>
	 * <li>Modrinth: project ID, version ID, file hash or name</li>
	 * <li>CurseForge: project ID, file ID, file ID (one file per release)</li>
	 * </ul>
	 */
	sealed interface Coordinate {

		/**
		 * Returns the owning provider identity.
		 *
		 * @return stable provider identity
		 */
		@NonNull
		Identity<Provider<?, ?>> providerId();

		/**
		 * Returns this level's opaque provider key.
		 *
		 * @return non-blank key
		 */
		@NonNull
		String key();

		/**
		 * Returns the project this coordinate belongs to, or itself for a project.
		 *
		 * @return owning project coordinates
		 */
		@NonNull
		Project project();

		/**
		 * Checks whether the other coordinate is this coordinate or addressed beneath
		 * it. A project includes its releases and their files, a release includes
		 * its files, and a file includes only itself. Dependency and conflict
		 * declarations match candidate content by this rule.
		 *
		 * @param other candidate coordinate
		 * @return whether this coordinate includes the other
		 */
		default boolean includes(@NonNull Coordinate other) {
			return switch (this) {
				case Project project -> project.equals(other.project());
				case Channel channel -> channel.equals(other) || other instanceof Build build && build.equals(build.channel());
				case Build build -> build.equals(other) || other instanceof File file && build.equals(file.build());
				case File file -> file.equals(other);
			};
		}

		/**
		 * Returns stable, unambiguous text of the form
		 * {@code provider/project[/build[/file]]}, with the provider UUID followed
		 * by each key URL-encoded as UTF-8. Equal coordinates, and only equal
		 * coordinates, share canonical text, so it is suitable for persistence and
		 * for deriving identities with {@link Identity#create(String)}.
		 *
		 * @return canonical coordinate text
		 */
		default String canonical() {
			return switch (this) {
				case Project project -> project.providerId().id() + "/" + encode(project.key());
				case Channel channel -> channel.project().canonical() + "/" + encode(channel.key());
				case Build build -> build.channel().canonical() + "/" + encode(build.key());
				case File file -> file.build().canonical() + "/" + encode(file.key());
			};
		}

		/**
		 * Parses canonical coordinate text produced by {@link #canonical()}. Only
		 * canonical text is accepted, so parsing and printing round-trip exactly.
		 *
		 * @param canonical canonical coordinate text
		 * @return project, release, or file coordinates
		 * @throws IllegalArgumentException when the text is not canonical
		 */
		static Coordinate parse(@NonNull String canonical) {
			String[] parts = canonical.split("/", -1);
			if (parts.length < 2 || parts.length > 5) {
				throw new IllegalArgumentException("Not canonical coordinate text: " + canonical);
			}
			Identity<Provider<?, ?>> providerId = Identity.create(UUID.fromString(parts[0]));
			Project project = new Project(providerId, decode(parts[1]));
			Coordinate result = switch (parts.length) {
				case 2:
					yield project;
				case 3:
					yield project.channel(decode(parts[2]));
				case 4:
					yield project.channel(decode(parts[2])).build(decode(parts[3]));
				case 5:
					yield project.channel(decode(parts[2])).build(decode(parts[3])).file(decode(parts[4]));
				default:
					throw new IllegalArgumentException("Not canonical coordinate text: " + canonical);
			};
			if (!result.canonical().equals(canonical)) {
				throw new IllegalArgumentException("Not canonical coordinate text: " + canonical);
			}
			return result;
		}

		/**
		 * A catalog project independent of its releases.
		 *
		 * @param providerId owning provider identity
		 * @param key        exact project key
		 */
		record Project(@NonNull Identity<Provider<?, ?>> providerId, @NonNull String key) implements Coordinate {

			/**
			 * Validates the opaque non-blank project key.
			 */
			public Project {
				validate(key);
			}

			@Override
			public Project project() {
				return this;
			}

			/**
			 * Addresses an exact release of this project.
			 *
			 * @param channelKey exact channel key
			 * @return release coordinates
			 * @throws IllegalArgumentException when the key is blank
			 */
			public Channel channel(@NonNull String channelKey) {
				return new Channel(this, channelKey);
			}
		}

		/**
		 * A provider release channel.
		 *
		 * @param project owning project
		 * @param key     exact channel key
		 */
		record Channel(@NonNull Project project, @NonNull String key) implements Coordinate {

			/**
			 * Validates the channel key.
			 */
			public Channel {
				validate(key);
			}

			@Override
			public Identity<Provider<?, ?>> providerId() {
				return project.providerId();
			}

			/**
			 * Addresses an exact payload of this release.
			 *
			 * @param buildKey provider file key, not necessarily a filesystem name
			 * @return file coordinates
			 * @throws IllegalArgumentException when the key is blank
			 */
			public Build build(@NonNull String buildKey) {
				return new Build(this, buildKey);
			}
		}

		/**
		 * An exact provider release.
		 *
		 * @param project owning project
		 * @param key     exact release key
		 */
		record Build(@NonNull Channel channel, @NonNull String key) implements Coordinate {

			/**
			 * Validates the release key.
			 */
			public Build {
				validate(key);
			}

			@Override
			public Identity<Provider<?, ?>> providerId() {
				return channel.providerId();
			}

			/**
			 * Addresses an exact payload of this release.
			 *
			 * @param fileKey provider file key, not necessarily a filesystem name
			 * @return file coordinates
			 * @throws IllegalArgumentException when the key is blank
			 */
			public File file(@NonNull String fileKey) {
				return new File(this, fileKey);
			}

			/**
			 * Returns the project owning this payload's release.
			 *
			 * @return owning project
			 */
			public Project project() {
				return channel().project();
			}
		}

		/**
		 * An exact payload within a release.
		 *
		 * @param build owning release
		 * @param key   provider file key, not necessarily a filesystem name
		 */
		record File(@NonNull Build build, @NonNull String key) implements Coordinate {

			/**
			 * Validates the payload key.
			 */
			public File {
				validate(key);
			}

			@Override
			public Identity<Provider<?, ?>> providerId() {
				return build.providerId();
			}

			/**
			 * Returns the project owning this payload's release.
			 *
			 * @return owning project
			 */
			public Project project() {
				return build.project();
			}
		}

		/**
		 * Checks a coordinate key for construction.
		 *
		 * @param key candidate key
		 * @throws IllegalArgumentException when the key is blank
		 */
		private static void validate(String key) {
			if (key.isBlank()) {
				throw new IllegalArgumentException("Provider coordinates must be non-blank.");
			}
		}

		/**
		 * Escapes an opaque key so separators in canonical text stay unambiguous.
		 *
		 * @param key opaque key
		 * @return URL-encoded key
		 */
		private static String encode(String key) {
			return URLEncoder.encode(key, StandardCharsets.UTF_8);
		}

		/**
		 * Reverses {@link #encode(String)}.
		 *
		 * @param encoded URL-encoded key
		 * @return opaque key
		 */
		private static String decode(String encoded) {
			return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
		}
	}
}