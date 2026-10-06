package cat.tabbie.sdk.addon;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Artifact.Relation;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;

import static cat.tabbie.sdk.TestFixtures.BUILD;
import static cat.tabbie.sdk.TestFixtures.CHANNEL;
import static cat.tabbie.sdk.TestFixtures.FABRIC_CLIENT;
import static cat.tabbie.sdk.TestFixtures.FABRIC_SERVER;
import static cat.tabbie.sdk.TestFixtures.PAPER_SERVER;
import static cat.tabbie.sdk.TestFixtures.PROJECT;
import static cat.tabbie.sdk.TestFixtures.QUILT_CLIENT;
import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.file;
import static cat.tabbie.sdk.TestFixtures.mod;
import static cat.tabbie.sdk.TestFixtures.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddonContractsTest {

	private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

	@Test
	void build_copiesItsLabelsAndArtifacts() {
		Set<Label> labels = new HashSet<>(Set.of(FABRIC_CLIENT));
		Set<Artifact> content = new HashSet<>(Set.of(mod("a", FABRIC_CLIENT)));

		Addon.Build build = new Addon.Build(BUILD, "1.0.0", EARLIER, 1L, labels, content);
		labels.add(FABRIC_SERVER);
		content.clear();

		assertEquals(Set.of(FABRIC_CLIENT), build.labels());
		assertEquals(1, build.content().size());
		assertThrows(UnsupportedOperationException.class, () -> build.content().clear());
	}

	@Test
	void build_rejectsBrokenPublicationInvariants() {
		Set<Artifact> content = Set.of(mod("a", FABRIC_CLIENT));
		Mod.Default foreign = new Mod.Default(CHANNEL.build("2.0.0").file("a"), "A", source("a.jar", bytes("a")),
				Set.of(FABRIC_CLIENT), Parity.UNKNOWN, Set.of());
		Mod.Default sameFile = new Mod.Default(file("a"), "Other name", source("b.jar", bytes("b")), Set.of(FABRIC_CLIENT),
				Parity.UNKNOWN, Set.of());

		assertThrows(IllegalArgumentException.class,
				() -> new Addon.Build(BUILD, " ", EARLIER, 1L, Set.of(FABRIC_CLIENT), content));
		assertThrows(IllegalArgumentException.class,
				() -> new Addon.Build(BUILD, "1.0.0", EARLIER, 0L, Set.of(FABRIC_CLIENT), content));
		assertThrows(IllegalArgumentException.class,
				() -> new Addon.Build(BUILD, "1.0.0", EARLIER, 1L, Set.of(FABRIC_CLIENT), Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Addon.Build(BUILD, "1.0.0", EARLIER, 1L, Set.of(FABRIC_CLIENT), Set.of(foreign)));
		assertThrows(IllegalArgumentException.class, () -> new Addon.Build(BUILD, "1.0.0", EARLIER, 1L,
				Set.of(FABRIC_CLIENT), Set.of(mod("a", FABRIC_CLIENT), sameFile)));
	}

	@Test
	void build_requiresAdvertisedLabels_supportedByEveryArtifact() {
		Set<Artifact> clientOnly = Set.of(mod("a", FABRIC_CLIENT));

		assertThrows(IllegalArgumentException.class,
				() -> new Addon.Build(BUILD, "1.0.0", EARLIER, 1L, Set.of(), clientOnly));
		assertThrows(IllegalArgumentException.class,
				() -> new Addon.Build(BUILD, "1.0.0", EARLIER, 1L, Set.of(FABRIC_CLIENT, FABRIC_SERVER), clientOnly));
	}

	@Test
	void build_rejectsArtifactsThatConflictWithEachOther() {
		Mod.Default library = mod("library", FABRIC_CLIENT);
		Mod.Default rival = mod("rival", Set.of(FABRIC_CLIENT), Set.of(new Relation.Incompatible(library.coordinates())));

		assertThrows(IllegalArgumentException.class,
				() -> new Addon.Build(BUILD, "1.0.0", EARLIER, 1L, Set.of(FABRIC_CLIENT), Set.of(library, rival)));
	}

	@Test
	void build_assessesCompatibility_asSupportedUnknownOrUnsupported() {
		Addon.Build build = Addon.Build.of(BUILD, "1.0.0", EARLIER, 1L, Set.of(FABRIC_CLIENT), mod("a", FABRIC_CLIENT));

		assertEquals(Compatibility.SUPPORTED, build.compatibility(FABRIC_CLIENT));
		assertEquals(Compatibility.UNKNOWN, build.compatibility(QUILT_CLIENT));
		assertEquals(Compatibility.UNSUPPORTED, build.compatibility(PAPER_SERVER));
	}

	@Test
	void build_exposesItsProvider_andProject() {
		Addon.Build build = Addon.Build.of(BUILD, "1.0.0", EARLIER, 1L, Set.of(FABRIC_CLIENT), mod("a", FABRIC_CLIENT));

		assertEquals(PROJECT, build.project());
		assertEquals(PROJECT.providerId(), build.providerId());
		assertEquals(Set.of(), build.packages());
	}

	@Test
	void addon_validatesItsBuilds() {
		Addon.Build build = build("1.0.0", CHANNEL, 1L, EARLIER);
		Coordinate.Project other = new Coordinate.Project(PROJECT.providerId(), "other");
		Addon.Build twin = new Addon.Build(BUILD, "Twin", LATER, 2L, Set.of(FABRIC_CLIENT), Set.of(mod("a", FABRIC_CLIENT)));

		assertThrows(IllegalArgumentException.class, () -> new Example(PROJECT, " ", Set.of()));
		assertThrows(IllegalArgumentException.class, () -> new Example(other, "Other", Set.of(build)));
		assertThrows(IllegalArgumentException.class, () -> new Example(PROJECT, "Example", Set.of(build, twin)));
	}

	@Test
	void addon_derivesIdentities_fromItsCoordinates() {
		Example addon = new Example(PROJECT, "Example", Set.of());

		assertEquals(Identity.create(PROJECT.canonical()), addon.addonId());
		assertEquals(PROJECT.providerId(), addon.providerId());
		assertEquals(Optional.empty(), addon.latest());
	}

	@Test
	void latest_ordersByReleaseNumberThenDate_andFiltersChannels() {
		Coordinate.Channel beta = PROJECT.channel("beta");
		Addon.Build stable = build("1.0.0", CHANNEL, 1L, EARLIER);
		Addon.Build sameNumberLater = build("1.0.1", CHANNEL, 1L, LATER);
		Addon.Build preview = build("2.0.0-beta", beta, 2L, EARLIER);

		Example addon = new Example(PROJECT, "Example", Set.of(stable, sameNumberLater, preview));

		assertEquals(Optional.of(preview), addon.latest());
		assertEquals(Optional.of(sameNumberLater), addon.latest(Set.of(CHANNEL)));
		assertEquals(Optional.empty(), addon.latest(Set.of(PROJECT.channel("alpha"))));
	}

	private static Addon.Build build(String key, Coordinate.Channel channel, long number, Instant date) {
		Coordinate.Build coordinates = channel.build(key);
		Mod.Default artifact = new Mod.Default(coordinates.file("jar"), "Example", source("example.jar", bytes(key)),
				Set.of(FABRIC_CLIENT), Parity.UNKNOWN, Set.of());
		return Addon.Build.of(coordinates, key, date, number, Set.of(FABRIC_CLIENT), artifact);
	}

	/**
	 * A minimal provider-owned addon record.
	 */
	record Example(Coordinate.Project coordinates, String addonName, Set<Build> builds) implements Addon {

		Example {
			builds = Set.copyOf(builds);
			Addon.validate(coordinates, addonName, builds);
		}
	}
}
