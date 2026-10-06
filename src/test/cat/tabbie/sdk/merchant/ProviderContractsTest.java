package cat.tabbie.sdk.merchant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.Addon;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Query;
import cat.tabbie.sdk.api.Queryable;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.merchant.Provider.Search;
import cat.tabbie.sdk.minecraft.Parity;

import static cat.tabbie.sdk.TestFixtures.FABRIC_CLIENT;
import static cat.tabbie.sdk.TestFixtures.PROVIDER_ID;
import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.collapse;
import static cat.tabbie.sdk.TestFixtures.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderContractsTest {

	private static final Coordinate.Project PROJECT = new Coordinate.Project(PROVIDER_ID, "example");

	private static final Coordinate.Channel STABLE = PROJECT.channel("stable");

	private static final Coordinate.Channel BETA = PROJECT.channel("beta");

	private static final Coordinate.Build RELEASE = STABLE.build("1.0.0");

	private static final Coordinate.File JAR = RELEASE.file("jar");

	@TempDir
	Path scratch;

	@Test
	void coordinates_navigateToTheirOwners() {
		assertEquals(PROJECT, JAR.project());
		assertEquals(PROJECT, RELEASE.project());
		assertEquals(PROJECT, STABLE.project());
		assertEquals(PROJECT, PROJECT.project());
		assertEquals(PROVIDER_ID, JAR.providerId());
		assertEquals(STABLE, JAR.build().channel());
	}

	@Test
	void coordinates_rejectBlankKeys() {
		assertThrows(IllegalArgumentException.class, () -> new Coordinate.Project(PROVIDER_ID, " "));
		assertThrows(IllegalArgumentException.class, () -> PROJECT.channel(""));
		assertThrows(IllegalArgumentException.class, () -> STABLE.build("\t"));
		assertThrows(IllegalArgumentException.class, () -> RELEASE.file(" "));
		assertThrows(NullPointerException.class, () -> new Coordinate.Project(null, "example"));
	}

	@Test
	void includes_matchesEachLevelAndEverythingBeneathIt() {
		Coordinate.Build betaRelease = BETA.build("2.0.0-beta");
		Coordinate.File betaJar = betaRelease.file("jar");
		Coordinate.File sources = RELEASE.file("sources");

		assertTrue(PROJECT.includes(PROJECT) && PROJECT.includes(STABLE) && PROJECT.includes(RELEASE)
				&& PROJECT.includes(JAR) && PROJECT.includes(betaJar));
		assertTrue(STABLE.includes(STABLE) && STABLE.includes(RELEASE) && STABLE.includes(JAR));
		assertFalse(STABLE.includes(PROJECT) || STABLE.includes(BETA) || STABLE.includes(betaRelease)
				|| STABLE.includes(betaJar));
		assertTrue(RELEASE.includes(RELEASE) && RELEASE.includes(JAR) && RELEASE.includes(sources));
		assertFalse(RELEASE.includes(STABLE) || RELEASE.includes(betaJar));
		assertTrue(JAR.includes(JAR));
		assertFalse(JAR.includes(sources) || JAR.includes(RELEASE));
	}

	@Test
	void includes_neverCrossesProviders() {
		Coordinate.Project foreign = new Coordinate.Project(Identity.create("test:other"), "example");

		assertFalse(PROJECT.includes(foreign));
		assertFalse(foreign.includes(JAR));
	}

	@Test
	void canonical_roundTripsEveryLevel_andEscapesSeparators() {
		Coordinate.File awkward = new Coordinate.Project(PROVIDER_ID, "a/b c+d%")
				.channel("ß")
				.build("1.0/rc 1")
				.file("~file~");

		for (Coordinate coordinate : List.of(PROJECT, STABLE, RELEASE, JAR, awkward, awkward.build(),
				awkward.build().channel())) {
			assertEquals(coordinate, Coordinate.parse(coordinate.canonical()));
		}
		assertEquals(PROVIDER_ID.id() + "/example/stable/1.0.0/jar", JAR.canonical());
		assertEquals(5, awkward.canonical().split("/").length);
	}

	@Test
	void parse_acceptsOnlyCanonicalText() {
		String id = PROVIDER_ID.id().toString();

		assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(id));
		assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(id + "/a/b/c/d/e"));
		assertThrows(IllegalArgumentException.class, () -> Coordinate.parse("not-a-uuid/example"));
		assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(id.toUpperCase() + "/example"));
		assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(id + "/a%2fb"));
		assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(id + "/a%zz"));
		assertThrows(IllegalArgumentException.class, () -> Coordinate.parse(id + "/example/"));
	}

	@Test
	void search_offersStandardCriteria_andRejectsBlankText() {
		Search.Default byId = Search.of(JAR);
		Search.Default byText = Search.text("sodium")
				.withTargets(Set.of(FABRIC_CLIENT))
				.withFamilies(Set.of(Mod.class))
				.withDuration(Duration.ofMinutes(5));

		assertEquals(Optional.of(JAR), byId.identifier());
		assertEquals(Optional.empty(), byId.text());
		assertEquals(Set.of(), byId.targets());
		assertEquals(Optional.of("sodium"), byText.text());
		assertEquals(Set.of(FABRIC_CLIENT), byText.targets());
		assertEquals(Set.of(Mod.class), byText.families());
		assertEquals(Optional.of(Duration.ofMinutes(5)), byText.duration());
		assertThrows(IllegalArgumentException.class, () -> Search.text(" "));
	}

	@Test
	void provider_mintsAndRecognizesItsOwnCoordinates() {
		Catalog catalog = new Catalog(Optional.of(List.of()));
		Coordinate.Project foreign = new Coordinate.Project(Identity.create("test:other"), "example");

		Coordinate.Project minted = catalog.project("example");

		assertEquals(PROJECT, minted);
		assertTrue(catalog.owns(minted.channel("stable").build("1").file("jar")));
		assertFalse(catalog.owns(foreign));
	}

	@Test
	void resolve_defersQueriesUntilCollapse() throws IOException {
		Addon.Build release = release();
		Catalog catalog = new Catalog(Optional.of(List.of(release)));

		Intermediate<Optional<Addon.Build>> byBuild = catalog.resolve(RELEASE);
		Intermediate<Optional<Addon.Build>> byFile = catalog.resolve(JAR);
		Intermediate<Optional<Addon>> byProject = catalog.resolve(PROJECT);
		int queriesBeforeCollapse = catalog.queries.get();

		assertEquals(0, queriesBeforeCollapse);
		assertEquals(Optional.of(release), collapse(byBuild, scratch));
		assertEquals(Optional.of(release), collapse(byFile, scratch));
		assertEquals(Optional.empty(), collapse(byProject, scratch));
		assertEquals(Search.of(JAR), catalog.lastSearch);
	}

	@Test
	void builds_listsAPageOfReleases() throws IOException {
		Addon.Build release = release();
		Catalog catalog = new Catalog(Optional.of(List.of(release)));

		Collection<Addon.Build> page = collapse(catalog.builds(PROJECT, Set.of(FABRIC_CLIENT), 10), scratch);

		assertEquals(List.of(release), List.copyOf(page));
		assertEquals(Search.of(PROJECT).withTargets(Set.of(FABRIC_CLIENT)), catalog.lastSearch);
		assertEquals(10, catalog.lastLimit);
	}

	@Test
	void builds_failsWhenTheProviderCannotList_ratherThanReportingNoReleases() {
		Catalog unavailable = new Catalog(Optional.empty());

		Intermediate<Collection<Addon.Build>> page = unavailable.builds(PROJECT, Set.of(), 10);

		assertThrows(IOException.class, () -> collapse(page, scratch));
		assertThrows(IllegalArgumentException.class, () -> unavailable.builds(PROJECT, Set.of(), 0));
	}

	private static Addon.Build release() {
		Mod.Default artifact = new Mod.Default(JAR, "Example", source("example.jar", bytes("example")),
				Set.of(FABRIC_CLIENT), Parity.UNKNOWN, Set.of());
		return Addon.Build.of(RELEASE, "1.0.0", Instant.EPOCH, 1L, Set.of(FABRIC_CLIENT), artifact);
	}

	/**
	 * An in-memory provider that answers release queries from a fixed list, or
	 * cannot answer at all, and records the last release query.
	 */
	private static final class Catalog implements Provider<Addon, Addon.Build> {

		final Optional<List<Addon.Build>> releases;

		final AtomicInteger queries = new AtomicInteger();

		Search lastSearch;

		Integer lastLimit;

		Catalog(Optional<List<Addon.Build>> releases) {
			this.releases = releases;
		}

		@Override
		public Identity<Provider<?, ?>> providerId() {
			return PROVIDER_ID;
		}

		@Override
		public String providerName() {
			return "Catalog";
		}

		@Override
		public Optional<Collection<Addon>> query(Query.Several<Search> query) {
			queries.incrementAndGet();
			return Optional.of(List.of());
		}

		@Override
		public Queryable<Search, Addon.Build> releases() {
			return query -> {
				queries.incrementAndGet();
				lastSearch = query.criteria();
				lastLimit = query.limit();
				return releases.map(found -> found.stream()
						.filter(build -> query.criteria().identifier()
								.map(coordinate -> coordinate.includes(build.coordinates())
										|| build.content().stream().anyMatch(artifact -> coordinate.equals(artifact.coordinates())))
								.orElse(true))
						.toList());
			};
		}
	}
}
