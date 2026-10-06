package cat.tabbie.sdk.merchant;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cat.tabbie.sdk.addon.Addon;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.minecraft.Parity;

import static cat.tabbie.sdk.TestFixtures.BUILD;
import static cat.tabbie.sdk.TestFixtures.CHANNEL;
import static cat.tabbie.sdk.TestFixtures.FABRIC_CLIENT;
import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.mod;
import static cat.tabbie.sdk.TestFixtures.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseContractsTest {

	@Test
	void publicationOrder_comparesReleaseNumbers_thenDates() {
		Addon.Build first = release("a", 1L, Instant.parse("2026-03-01T00:00:00Z"));
		Addon.Build tied = release("b", 1L, Instant.parse("2026-04-01T00:00:00Z"));
		Addon.Build second = release("c", 2L, Instant.parse("2026-01-01T00:00:00Z"));
		List<Release<?>> releases = new ArrayList<>(List.of(second, tied, first));

		releases.sort(Release.PUBLICATION_ORDER);

		assertEquals(List.of(first, tied, second), releases);
	}

	@Test
	void validate_requiresANameAndAPositiveNumber() {
		Release.validate("1.0.0", 1L);

		assertThrows(IllegalArgumentException.class, () -> Release.validate(" ", 1L));
		assertThrows(IllegalArgumentException.class, () -> Release.validate("1.0.0", 0L));
		assertThrows(NullPointerException.class, () -> Release.validate(null, 1L));
	}

	@Test
	void payloadValidate_requiresDistinctFilesOfTheRelease() {
		Mod.Default inRelease = mod("a", FABRIC_CLIENT);
		Mod.Default elsewhere = new Mod.Default(CHANNEL.build("2.0.0").file("a"), "A", inRelease.source(),
				Set.of(FABRIC_CLIENT), inRelease.parity(), Set.of());

		Release.Payload.validate(BUILD, Set.of(inRelease));

		assertThrows(IllegalArgumentException.class, () -> Release.Payload.validate(BUILD, Set.of()));
		assertThrows(IllegalArgumentException.class, () -> Release.Payload.validate(BUILD, Set.of(elsewhere)));
	}

	private static Addon.Build release(String key, long number, Instant date) {
		Provider.Coordinate.Build coordinates = CHANNEL.build(key);
		Mod.Default artifact = new Mod.Default(coordinates.file("jar"), key, source(key + ".jar", bytes(key)),
				Set.of(FABRIC_CLIENT), Parity.UNKNOWN, Set.of());
		return Addon.Build.of(coordinates, key, date, number, Set.of(FABRIC_CLIENT), artifact);
	}
}
