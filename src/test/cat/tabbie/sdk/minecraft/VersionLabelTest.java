package cat.tabbie.sdk.minecraft;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import cat.tabbie.sdk.minecraft.distribution.Bedrock;
import cat.tabbie.sdk.minecraft.distribution.Distribution;
import cat.tabbie.sdk.minecraft.distribution.Java;

import static cat.tabbie.sdk.TestFixtures.BEDROCK_VERSION;
import static cat.tabbie.sdk.TestFixtures.FABRIC_CLIENT;
import static cat.tabbie.sdk.TestFixtures.JAVA_VERSION;
import static cat.tabbie.sdk.TestFixtures.PAPER_SERVER;
import static cat.tabbie.sdk.TestFixtures.QUILT_CLIENT;
import static cat.tabbie.sdk.TestFixtures.mod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionLabelTest {

	@Test
	void version_normalizesItsIdentifier() {
		Version.Java version = new Version.Java("  1.21.1-RC1 ", Version.Java.Release.RELEASE_CANDIDATE);

		assertEquals("1.21.1-rc1", version.id());
		assertEquals(Optional.empty(), version.releaseDate());
	}

	@Test
	void version_rejectsBlankOrQualifiedIdentifiers() {
		for (String invalid : new String[] { "", "  ", "1.21 .1", "java:1.21.1", "1.21/1", "1.21 1" }) {
			assertThrows(IllegalArgumentException.class, () -> new Version.Java(invalid, Version.Java.Release.UNKNOWN),
					invalid);
			assertThrows(IllegalArgumentException.class,
					() -> new Version.Bedrock(invalid, Version.Bedrock.Release.UNKNOWN), invalid);
		}
	}

	@Test
	void match_comparesEditionAndIdentifier_ignoringReleaseMetadata() {
		Version.Java dated = new Version.Java("1.21.1", Version.Java.Release.RELEASE, Instant.EPOCH);
		Version.Java undated = new Version.Java("1.21.1", Version.Java.Release.UNKNOWN);
		Version.Bedrock bedrock = new Version.Bedrock("1.21.1", Version.Bedrock.Release.RELEASE);

		assertTrue(dated.match(undated));
		assertNotEquals(dated, undated);
		assertFalse(dated.match(bedrock));
		assertFalse(dated.match(new Version.Java("1.21.2", Version.Java.Release.RELEASE)));
	}

	@Test
	void canonical_roundTripsWithoutReleaseMetadata() {
		Version.Java dated = new Version.Java("1.21.1", Version.Java.Release.RELEASE, Instant.EPOCH);

		Version parsed = Version.parse(dated.canonical());

		assertEquals("java:1.21.1", dated.canonical());
		assertEquals("bedrock:1.21.80", BEDROCK_VERSION.canonical());
		assertEquals(new Version.Java("1.21.1", Version.Java.Release.UNKNOWN), parsed);
		assertTrue(parsed.match(dated));
		assertEquals(BEDROCK_VERSION.id(), Version.parse("bedrock:1.21.80").id());
	}

	@Test
	void parse_acceptsOnlyCanonicalText() {
		for (String invalid : new String[] { "1.21.1", "JAVA:1.21.1", "forge:1.21.1", "java:", "java:1.21.1-RC1",
				"java: 1.21.1" }) {
			assertThrows(IllegalArgumentException.class, () -> Version.parse(invalid), invalid);
		}
	}

	@Test
	void interpret_readsLooselyWrittenText_defaultingToJavaEdition() {
		assertEquals(JAVA_VERSION.id(), assertInstanceOf(Version.Java.class, Version.interpret("1.21.1")).id());
		assertInstanceOf(Version.Java.class, Version.interpret("Minecraft Java Edition 1.21.1"));
		assertInstanceOf(Version.Java.class, Version.interpret("minecraft: java: 1.21.1"));
		assertEquals("1.21.80", assertInstanceOf(Version.Bedrock.class, Version.interpret("Bedrock 1.21.80")).id());
		assertInstanceOf(Version.Bedrock.class, Version.interpret("MINECRAFT BEDROCK EDITION: 1.21.80"));
		assertThrows(IllegalArgumentException.class, () -> Version.interpret("   "));
		assertThrows(IllegalArgumentException.class, () -> Version.interpret("java 1.21 .1"));
	}

	@Test
	void label_rejectsVersionsAndEnvironmentsItsDistributionCannotRun() {
		assertThrows(IllegalArgumentException.class,
				() -> new Label(BEDROCK_VERSION, Java.Launcher.Of.FABRIC, Environment.CLIENT));
		assertThrows(IllegalArgumentException.class,
				() -> new Label(JAVA_VERSION, Bedrock.Of.NATIVE, Environment.CLIENT));
		assertThrows(IllegalArgumentException.class,
				() -> new Label(JAVA_VERSION, Java.Manager.Of.PAPER, Environment.CLIENT));
		assertThrows(IllegalArgumentException.class,
				() -> new Label(BEDROCK_VERSION, Bedrock.Manager.Of.ENDSTONE, Environment.CLIENT));
	}

	@Test
	void label_canonicalTextRoundTrips_forEveryDistributionAndEnvironment() {
		int labels = 0;

		for (Distribution distribution : Distribution.values()) {
			Version version = distribution instanceof Java ? JAVA_VERSION : BEDROCK_VERSION;
			for (Environment environment : distribution.environments()) {
				Label label = new Label(version, distribution, environment);

				Label parsed = Label.parse(label.canonical());

				assertTrue(parsed.match(label), label.canonical());
				assertEquals(label.canonical(), parsed.canonical());
				labels++;
			}
		}

		assertEquals("java:fabric/1.21.1/client", FABRIC_CLIENT.canonical());
		assertTrue(labels > Distribution.values().size());
	}

	@Test
	void labelParse_rejectsMalformedOrUnknownTargets() {
		for (String invalid : new String[] { "java:fabric/1.21.1", "java:fabric/1.21.1/client/extra",
				"java:unknown/1.21.1/client", "java:fabric/1.21.1/CLIENT", "java:fabric/1.21.1/desktop",
				"java:paper/1.21.1/client", "java:fabric/1.21.1-RC1/client" }) {
			assertThrows(IllegalArgumentException.class, () -> Label.parse(invalid), invalid);
		}
	}

	@Test
	void label_matchesTargetIdentity_ignoringVersionMetadata() {
		Label dated = new Label(new Version.Java("1.21.1", Version.Java.Release.RELEASE, Instant.EPOCH),
				Java.Launcher.Of.FABRIC, Environment.CLIENT);

		assertTrue(dated.match(FABRIC_CLIENT));
		assertFalse(FABRIC_CLIENT.match(QUILT_CLIENT));
		assertFalse(FABRIC_CLIENT.match(new Label(JAVA_VERSION, Java.Launcher.Of.FABRIC, Environment.SERVER)));
	}

	@Test
	void compatibility_rejectsUnsupportedFamilies_beforeConsultingDeclarations() {
		var fabricMod = mod("a", FABRIC_CLIENT);

		assertEquals(Compatibility.SUPPORTED, FABRIC_CLIENT.compatibility(fabricMod));
		assertEquals(Compatibility.UNKNOWN, QUILT_CLIENT.compatibility(fabricMod));
		assertEquals(Compatibility.UNSUPPORTED, PAPER_SERVER.compatibility(fabricMod));
	}
}
