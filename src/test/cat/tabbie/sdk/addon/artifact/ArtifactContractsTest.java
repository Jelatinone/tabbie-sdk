package cat.tabbie.sdk.addon.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.TestFixtures.MockRetention;
import cat.tabbie.sdk.TestFixtures.MockSource;
import cat.tabbie.sdk.addon.artifact.Artifact.Relation;
import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Relative;

import static cat.tabbie.sdk.TestFixtures.BEDROCK_CLIENT;
import static cat.tabbie.sdk.TestFixtures.BEDROCK_SERVER;
import static cat.tabbie.sdk.TestFixtures.BUILD;
import static cat.tabbie.sdk.TestFixtures.CHANNEL;
import static cat.tabbie.sdk.TestFixtures.ENDSTONE_SERVER;
import static cat.tabbie.sdk.TestFixtures.FABRIC_CLIENT;
import static cat.tabbie.sdk.TestFixtures.FABRIC_SERVER;
import static cat.tabbie.sdk.TestFixtures.PAPER_SERVER;
import static cat.tabbie.sdk.TestFixtures.PLATFORM;
import static cat.tabbie.sdk.TestFixtures.PROJECT;
import static cat.tabbie.sdk.TestFixtures.QUILT_CLIENT;
import static cat.tabbie.sdk.TestFixtures.VANILLA_CLIENT;
import static cat.tabbie.sdk.TestFixtures.VANILLA_SERVER;
import static cat.tabbie.sdk.TestFixtures.WORLD;
import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.collapse;
import static cat.tabbie.sdk.TestFixtures.context;
import static cat.tabbie.sdk.TestFixtures.entries;
import static cat.tabbie.sdk.TestFixtures.file;
import static cat.tabbie.sdk.TestFixtures.mod;
import static cat.tabbie.sdk.TestFixtures.source;
import static cat.tabbie.sdk.TestFixtures.zip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtifactContractsTest {

	private static final Installer<Artifact.Context> NOTHING = context -> Intermediate.of(Set.of());

	@TempDir
	Path scratch;

	@Test
	void everyFamily_constructsDefaultAndCustomVariants_forASupportedTarget() {
		List<Artifact> artifacts = everyVariant();

		assertEquals(14, artifacts.size());
		assertEquals(14, artifacts.stream().map(Object::getClass).distinct().count());
		for (Artifact artifact : artifacts) {
			Label label = artifact.labels().iterator().next();
			assertEquals(Compatibility.SUPPORTED, label.compatibility(artifact), artifact.getClass().getName());
			assertEquals(artifact instanceof Artifact.Custom, artifact.getClass().getSimpleName().equals("Custom"));
		}
	}

	@Test
	void construction_copiesCollections() {
		Set<Label> labels = new HashSet<>(Set.of(FABRIC_CLIENT));
		Set<Relation> relations = new HashSet<>(Set.of(new Relation.Optional(PROJECT.channel("other"))));

		Mod.Default artifact = new Mod.Default(file("a"), "A", source("a.jar", bytes("a")), labels, Parity.UNKNOWN,
				relations);
		labels.add(FABRIC_SERVER);
		relations.clear();

		assertEquals(Set.of(FABRIC_CLIENT), artifact.labels());
		assertEquals(1, artifact.relations().size());
		assertThrows(UnsupportedOperationException.class, () -> artifact.labels().add(FABRIC_SERVER));
	}

	@Test
	void construction_rejectsMissingDeclarations_andUnsupportedFamilies() {
		MockSource source = source("a.jar", bytes("a"));

		assertThrows(IllegalArgumentException.class,
				() -> new Mod.Default(file("a"), " ", source, Set.of(FABRIC_CLIENT), Parity.UNKNOWN, Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Mod.Default(file("a"), "A", source, Set.of(), Parity.UNKNOWN, Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Mod.Default(file("a"), "A", source, Set.of(PAPER_SERVER), Parity.UNKNOWN, Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Plugin.Default(file("a"), "A", source, Set.of(FABRIC_SERVER), Parity.UNKNOWN, Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Behaviourpack.Default(file("a"), "A", source, Set.of(ENDSTONE_SERVER), Parity.UNKNOWN, Set.of()));
		assertThrows(NullPointerException.class,
				() -> new Mod.Default(file("a"), "A", null, Set.of(FABRIC_CLIENT), Parity.UNKNOWN, Set.of()));
	}

	@Test
	void construction_rejectsRelationsThatIncludeThisArtifact_atEveryLevel() {
		for (Coordinate self : List.<Coordinate>of(file("a"), BUILD, CHANNEL, PROJECT)) {
			assertThrows(IllegalArgumentException.class,
					() -> mod("a", Set.of(FABRIC_CLIENT), Set.of(new Relation.Required(self))), self.toString());
		}
	}

	@Test
	void construction_rejectsContradictoryRelations() {
		Coordinate.Project other = new Coordinate.Project(PROJECT.providerId(), "other");
		Coordinate.File otherFile = other.channel("release").build("2.0.0").file("jar");

		assertThrows(IllegalArgumentException.class, () -> mod("a", Set.of(FABRIC_CLIENT),
				Set.of(new Relation.Required(other), new Relation.Optional(other))));
		assertThrows(IllegalArgumentException.class, () -> mod("a", Set.of(FABRIC_CLIENT),
				Set.of(new Relation.Incompatible(other), new Relation.Optional(otherFile))));
		assertThrows(IllegalArgumentException.class, () -> mod("a", Set.of(FABRIC_CLIENT),
				Set.of(new Relation.Incompatible(other.channel("release")), new Relation.Required(otherFile))));
	}

	@Test
	void relations_areSelectedByKind() {
		Coordinate.Project required = new Coordinate.Project(PROJECT.providerId(), "library");
		Coordinate.Project conflicting = new Coordinate.Project(PROJECT.providerId(), "rival");
		Coordinate.Project optional = new Coordinate.Project(PROJECT.providerId(), "addon");
		Coordinate.Project embedded = new Coordinate.Project(PROJECT.providerId(), "shaded");

		Mod.Default artifact = mod("a", Set.of(FABRIC_CLIENT), Set.of(
				new Relation.Required(required),
				new Relation.Incompatible(conflicting),
				new Relation.Optional(optional),
				new Relation.Embedded(embedded)));

		assertEquals(Set.of(required), artifact.depends());
		assertEquals(Set.of(conflicting), artifact.conflicts());
		assertEquals(Set.of(optional), artifact.coordinates(Relation.Optional.class));
		assertEquals(Set.of(embedded), artifact.coordinates(Relation.Embedded.class));
	}

	@Test
	void artifactId_derivesFromFileCoordinates() {
		Mod.Default first = mod("a", FABRIC_CLIENT);
		Mod.Default renamed = new Mod.Default(file("a"), "Renamed", source("x.jar", bytes("x")), Set.of(FABRIC_SERVER),
				Parity.ONLY_SERVER, Set.of());

		assertEquals(first.artifactId(), renamed.artifactId());
		assertEquals(Identity.create(file("a").canonical()), first.artifactId());
		assertNotEquals(first.artifactId(), mod("b", FABRIC_CLIENT).artifactId());
	}

	@Test
	void install_placesANamedFile_inTheDistributionDefaultDirectory() throws IOException {
		MockRetention retention = new MockRetention();
		Mod.Default artifact = mod("example", FABRIC_CLIENT);

		Intermediate<Set<Image<?>>> work = artifact.install(context(FABRIC_CLIENT, retention));
		int opensBeforeCollapse = ((MockSource) artifact.source()).opens();
		Set<Image<?>> images = collapse(work, scratch);

		assertEquals(0, opensBeforeCollapse);
		Image.Installation image = assertInstanceOf(Image.Installation.class, images.iterator().next());
		assertEquals(1, images.size());
		assertEquals(Relative.root("mods", "example.jar"), image.target().relative());
		Reference.Captured captured = assertInstanceOf(Image.File.Present.class, image.after()).reference();
		assertArrayEquals(bytes("example"), retention.read(captured));
		assertThrows(UnsupportedOperationException.class, () -> images.add(image));
	}

	@Test
	void install_unpacksWorldContent_beneathItsOwnDirectory() throws IOException {
		MockRetention retention = new MockRetention();
		Datapack.Default artifact = new Datapack.Default(file("pack"), "Pack",
				source("pack.zip", zip(entries("pack.mcmeta", "{}", "data/x/function/y.mcfunction", "say hi"))),
				Set.of(VANILLA_SERVER), Parity.ONLY_SERVER, Set.of());

		Set<Image<?>> images = collapse(artifact.install(context(VANILLA_SERVER, retention)), scratch);

		String directory = artifact.artifactId().id().toString();
		assertEquals(Set.of(
				Relative.world("datapacks", directory, "pack.mcmeta"),
				Relative.world("datapacks", directory, "data", "x", "function", "y.mcfunction")),
				images.stream().map(image -> ((Image.Target.File) image.target()).relative()).collect(Collectors.toSet()));
	}

	@Test
	void install_requiresExplicitTargetSupport() {
		Mod.Default fabricOnly = mod("example", FABRIC_CLIENT);
		MockRetention retention = new MockRetention();

		assertThrows(IllegalArgumentException.class, () -> fabricOnly.install(context(QUILT_CLIENT, retention)));
		assertThrows(IllegalArgumentException.class, () -> fabricOnly.install(context(FABRIC_SERVER, retention)));
	}

	@Test
	void install_failsWithoutADefaultLayout_unlessTheCallerOverridesIt() throws IOException {
		MockRetention retention = new MockRetention();
		Resourcepack.Java.Default pack = new Resourcepack.Java.Default(file("pack"), "Pack",
				source("pack.zip", bytes("zip")), Set.of(VANILLA_SERVER), Parity.UNKNOWN, Set.of());
		Artifact.Layout delivered = new Artifact.Layout(false, Relative.root("delivery"));
		Artifact.Context.Default overridden = new Artifact.Context.Default(VANILLA_SERVER, PLATFORM,
				retention, Relative.root(), WORLD, Map.of(pack.artifactId(), delivered));

		Set<Image<?>> images = collapse(pack.install(overridden), scratch);

		assertThrows(IOException.class, () -> pack.install(context(VANILLA_SERVER, retention)));
		assertEquals(new Image.Target.File(Relative.root("delivery", "pack.zip")), images.iterator().next().target());
	}

	@Test
	void customInstaller_receivesTheContext_andItsImagesAreValidated() throws IOException {
		MockRetention retention = new MockRetention();
		List<Artifact.Context> seen = new ArrayList<>();
		Reference.Captured content = Reference.of("a.cfg", bytes("a"));
		Installer<Artifact.Context> duplicate = context -> {
			seen.add(context);
			return Intermediate.of(new HashSet<>(List.of(
					Image.create(Relative.root("config", "a.cfg"), content),
					Image.configure(Relative.root("config", "a.cfg"), "a\n", "b\n"))));
		};
		Mod.Custom artifact = new Mod.Custom(file("a"), "A", source("a.jar", bytes("a")), Set.of(FABRIC_CLIENT),
				Parity.UNKNOWN, Set.of(), duplicate);
		Artifact.Context.Default context = context(FABRIC_CLIENT, retention);

		Intermediate<Set<Image<?>>> work = artifact.install(context);

		assertEquals(List.of(context), seen);
		assertThrows(IllegalArgumentException.class, () -> collapse(work, scratch));
	}

	@Test
	void customInstaller_isNotInvoked_forAnUnsupportedTarget() {
		AtomicInteger calls = new AtomicInteger();
		Mod.Custom artifact = new Mod.Custom(file("a"), "A", source("a.jar", bytes("a")), Set.of(FABRIC_CLIENT),
				Parity.UNKNOWN, Set.of(), context -> {
					calls.incrementAndGet();
					return Intermediate.of(Set.of());
				});

		assertThrows(IllegalArgumentException.class, () -> artifact.install(context(QUILT_CLIENT, new MockRetention())));
		assertEquals(0, calls.get());
	}

	@Test
	void contextDefault_copiesLayoutOverrides() {
		Map<Identity<Artifact>, Artifact.Layout> layouts = new HashMap<>();

		Artifact.Context.Default context = new Artifact.Context.Default(FABRIC_CLIENT,
				PLATFORM, new MockRetention(), Relative.root(), WORLD,
				layouts);
		layouts.put(mod("a", FABRIC_CLIENT).artifactId(), new Artifact.Layout(false, Relative.root()));

		assertEquals(Map.of(), context.layouts());
		assertEquals(Archive.DEFAULT, context.archive());
	}

	/**
	 * Every built-in variant, each declared for a target supporting its family.
	 */
	static List<Artifact> everyVariant() {
		MockSource jar = source("a.jar", bytes("a"));
		MockSource zip = source("a.zip", zip(entries("a.txt", "a")));
		Set<Relation> none = Set.of();
		Parity parity = Parity.UNKNOWN;
		return List.of(
				new Mod.Default(file("mod"), "Mod", jar, Set.of(FABRIC_CLIENT), parity, none),
				new Mod.Custom(file("mod"), "Mod", jar, Set.of(FABRIC_CLIENT), parity, none, NOTHING),
				new Plugin.Default(file("plugin"), "Plugin", jar, Set.of(PAPER_SERVER), parity, none),
				new Plugin.Custom(file("plugin"), "Plugin", jar, Set.of(PAPER_SERVER), parity, none, NOTHING),
				new Datapack.Default(file("data"), "Data", zip, Set.of(VANILLA_SERVER), parity, none),
				new Datapack.Custom(file("data"), "Data", zip, Set.of(VANILLA_SERVER), parity, none, NOTHING),
				new Resourcepack.Java.Default(file("res"), "Res", zip, Set.of(VANILLA_CLIENT), parity, none),
				new Resourcepack.Java.Custom(file("res"), "Res", zip, Set.of(VANILLA_CLIENT), parity, none, NOTHING),
				new Resourcepack.Bedrock.Default(file("res"), "Res", zip, Set.of(BEDROCK_CLIENT), parity, none),
				new Resourcepack.Bedrock.Custom(file("res"), "Res", zip, Set.of(BEDROCK_CLIENT), parity, none, NOTHING),
				new Behaviourpack.Default(file("beh"), "Beh", zip, Set.of(BEDROCK_SERVER), parity, none),
				new Behaviourpack.Custom(file("beh"), "Beh", zip, Set.of(BEDROCK_SERVER), parity, none, NOTHING),
				new Modpack.Default(file("pack"), "Pack", zip, Set.of(FABRIC_CLIENT), parity, none),
				new Modpack.Custom(file("pack"), "Pack", zip, Set.of(FABRIC_CLIENT), parity, none, NOTHING));
	}
}
