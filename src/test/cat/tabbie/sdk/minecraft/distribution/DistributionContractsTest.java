package cat.tabbie.sdk.minecraft.distribution;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.TestFixtures;
import cat.tabbie.sdk.TestFixtures.MockRetention;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Behaviourpack;
import cat.tabbie.sdk.addon.artifact.Datapack;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.addon.artifact.Modpack;
import cat.tabbie.sdk.addon.artifact.Plugin;
import cat.tabbie.sdk.addon.artifact.Resourcepack;
import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Relative;
import cat.tabbie.sdk.platform.Toolchain;

import static cat.tabbie.sdk.TestFixtures.BEDROCK_CLIENT;
import static cat.tabbie.sdk.TestFixtures.BEDROCK_SERVER;
import static cat.tabbie.sdk.TestFixtures.BEDROCK_VERSION;
import static cat.tabbie.sdk.TestFixtures.BUILD;
import static cat.tabbie.sdk.TestFixtures.ENDSTONE_SERVER;
import static cat.tabbie.sdk.TestFixtures.FABRIC_CLIENT;
import static cat.tabbie.sdk.TestFixtures.FABRIC_SERVER;
import static cat.tabbie.sdk.TestFixtures.JAVA_VERSION;
import static cat.tabbie.sdk.TestFixtures.PAPER_SERVER;
import static cat.tabbie.sdk.TestFixtures.PLATFORM;
import static cat.tabbie.sdk.TestFixtures.PROJECT;
import static cat.tabbie.sdk.TestFixtures.VANILLA_CLIENT;
import static cat.tabbie.sdk.TestFixtures.VANILLA_SERVER;
import static cat.tabbie.sdk.TestFixtures.WORLD;
import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.context;
import static cat.tabbie.sdk.TestFixtures.file;
import static cat.tabbie.sdk.TestFixtures.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionContractsTest {

	private static final Platform WINDOWS = new Platform(Platform.Kernel.WINDOWS, Platform.Architecture.X_64);

	@Test
	void values_listEveryDistribution_javaFirst_withUniqueValidIdentifiers() {
		List<Distribution> values = Distribution.values();

		assertEquals(Java.values().size() + Bedrock.values().size(), values.size());
		assertEquals(values.size(), values.stream().map(Distribution::id).distinct().count());
		assertTrue(values.getFirst() instanceof Java);
		assertTrue(values.getLast() instanceof Bedrock);
		for (Distribution distribution : values) {
			Distribution.validate(distribution.id());
			assertSame(distribution, Distribution.of(distribution.id()).orElseThrow());
		}
		assertEquals(Optional.empty(), Distribution.of("java:Fabric"));
	}

	@Test
	void validate_requiresANamespacedLowercaseIdentifier() {
		for (String invalid : new String[] { "fabric", "Java:fabric", "java:", "java:Fabric", "java:fab ric",
				"java:-fabric", "java:fabric-", "pocket:nukkit" }) {
			assertThrows(IllegalArgumentException.class, () -> Distribution.validate(invalid), invalid);
		}
	}

	@Test
	void environmentsAndEditions_followTheFamily() {
		assertEquals(Set.of(Environment.CLIENT, Environment.SERVER), Java.Launcher.Of.FABRIC.environments());
		assertEquals(Set.of(Environment.SERVER), Java.Manager.Of.PAPER.environments());
		assertEquals(Set.of(Environment.SERVER), Bedrock.Manager.Of.ENDSTONE.environments());
		assertTrue(Java.Of.NATIVE.applicable(JAVA_VERSION));
		assertFalse(Java.Of.NATIVE.applicable(BEDROCK_VERSION));
		assertTrue(Bedrock.Of.NATIVE.applicable(BEDROCK_VERSION));
	}

	@Test
	void supports_acceptsFamiliesAndTheirImplementations() {
		assertTrue(Java.Launcher.Of.FABRIC.supports(Mod.class));
		assertTrue(Java.Launcher.Of.FABRIC.supports(Mod.Default.class));
		assertTrue(Java.Launcher.Of.FABRIC.supports(Modpack.Custom.class));
		assertFalse(Java.Launcher.Of.FABRIC.supports(Plugin.class));
		assertTrue(Java.Manager.Of.PAPER.supports(Plugin.Default.class));
		assertFalse(Java.Manager.Of.PAPER.supports(Mod.class));
		assertFalse(Java.Manager.Of.PAPER.supports(Modpack.class));
		assertFalse(Java.Of.NATIVE.supports(Mod.class));
		assertTrue(Java.Of.NATIVE.supports(Resourcepack.Java.Default.class));
		assertFalse(Java.Of.NATIVE.supports(Resourcepack.Bedrock.class));
		assertFalse(Java.Of.NATIVE.supports(Resourcepack.class));
		assertTrue(Bedrock.Manager.Of.ENDSTONE.supports(Plugin.class));
		assertFalse(Bedrock.Manager.Of.ENDSTONE.supports(Behaviourpack.class));
		assertTrue(Bedrock.Of.NATIVE.supports(Behaviourpack.Default.class));
	}

	@Test
	void layout_placesEachFamily_inItsCommonDirectory() throws IOException {
		assertEquals(new Artifact.Layout(false, Relative.root("mods")), layout(artifact(Mod.class, FABRIC_CLIENT)));
		assertEquals(new Artifact.Layout(false, Relative.root("plugins")), layout(artifact(Plugin.class, PAPER_SERVER)));
		assertEquals(new Artifact.Layout(false, Relative.root("plugins")),
				layout(artifact(Plugin.class, ENDSTONE_SERVER)));
		assertEquals(new Artifact.Layout(false, Relative.root("resourcepacks")),
				layout(artifact(Resourcepack.Java.class, VANILLA_CLIENT)));
		assertEquals(new Artifact.Layout(true, Relative.root()), layout(artifact(Modpack.class, FABRIC_SERVER)));
	}

	@Test
	void layout_unpacksPacks_beneathDirectoriesNamedByArtifactIdentity() throws IOException {
		Artifact datapack = artifact(Datapack.class, VANILLA_SERVER);
		Artifact behaviour = artifact(Behaviourpack.class, BEDROCK_SERVER);
		Artifact resources = artifact(Resourcepack.Bedrock.class, BEDROCK_CLIENT);

		assertEquals(new Artifact.Layout(true, Relative.world("datapacks", directory(datapack))), layout(datapack));
		assertEquals(new Artifact.Layout(true, Relative.world("behavior_packs", directory(behaviour))),
				layout(behaviour));
		assertEquals(new Artifact.Layout(true, Relative.root("resource_packs", directory(resources))),
				layout(resources));
	}

	@Test
	void layout_requiresAnOverride_whereNoCommonDefaultExists() {
		assertThrows(IOException.class, () -> layout(artifact(Resourcepack.Java.class, VANILLA_SERVER)));
		assertThrows(IOException.class, () -> layout(artifact(Modpack.class, VANILLA_CLIENT)));
		assertThrows(IOException.class, () -> layout(artifact(Modpack.class, BEDROCK_CLIENT)));
	}

	@Test
	void layout_rejectsUnsupportedEnvironmentsAndFamilies() {
		Artifact.Context fabricClient = context(FABRIC_CLIENT, new MockRetention());
		Artifact.Context fabricServer = context(FABRIC_SERVER, new MockRetention());

		assertThrows(IllegalArgumentException.class,
				() -> Java.Manager.Of.PAPER.layout(artifact(Plugin.class, PAPER_SERVER), fabricClient));
		assertThrows(IllegalArgumentException.class,
				() -> Java.Manager.Of.PAPER.layout(artifact(Mod.class, FABRIC_SERVER), fabricServer));
	}

	@Test
	void byproduct_validatesItsDeclarations() {
		Label quiltServer = new Label(JAVA_VERSION, Java.Launcher.Of.QUILT, Environment.SERVER);

		Byproduct.validate("Server", Set.of(FABRIC_SERVER, FABRIC_CLIENT));

		assertThrows(IllegalArgumentException.class, () -> Byproduct.validate(" ", Set.of(FABRIC_SERVER)));
		assertThrows(IllegalArgumentException.class, () -> Byproduct.validate("Server", Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> Byproduct.validate("Server", Set.of(FABRIC_SERVER, quiltServer)));
	}

	@Test
	void byproduct_supportsMatchingTargets_onAcceptedPlatforms() {
		ServerJar universal = ServerJar.of("server", Set.of());
		ServerJar linuxOnly = ServerJar.of("native", Set.of(PLATFORM));

		assertTrue(universal.supports(PAPER_SERVER, WINDOWS));
		assertFalse(universal.supports(FABRIC_SERVER, WINDOWS));
		assertTrue(linuxOnly.supports(PAPER_SERVER, PLATFORM));
		assertFalse(linuxOnly.supports(PAPER_SERVER, WINDOWS));
		assertEquals(Identity.create(file("server").canonical()), universal.byproductId());
	}

	@Test
	void executable_describesHowItStartsAndStops_withoutRunningAnything() {
		ServerJar server = ServerJar.of("server", Set.of());
		Byproduct.Context context = new Byproduct.Context.Default(PAPER_SERVER, PLATFORM, new MockRetention(),
				Relative.root("servers", "lobby"), WORLD, Optional.of(4L << 30), Map.of());
		Command.Resolver resolver = new Command.Resolver() {

			@Override
			public Path path(Relative relative) {
				return Path.of("srv").resolve(context.resolve(relative).relativePath());
			}

			@Override
			public Path toolchain(Toolchain toolchain) {
				return Path.of("jdk", "bin", "java");
			}
		};

		Byproduct.Executable.Allocate start = server.allocate(context);
		Byproduct.Executable.Deallocate stop = server.deallocate(context);

		Byproduct.Executable.Allocate.Managed managed = assertInstanceOf(Byproduct.Executable.Allocate.Managed.class,
				start);
		assertEquals(List.of(Path.of("jdk", "bin", "java").toString(), "-Xmx" + (4L << 30), "-jar",
				Path.of("srv", "servers", "lobby", "server.jar").toString(), "--nogui"), managed.command().render(resolver));
		assertEquals(new Byproduct.Executable.Deallocate.Input("stop", Duration.ofSeconds(30)), stop);
	}

	@Test
	void managedAllocation_copiesItsEnvironment() {
		Map<String, String> environment = new HashMap<>(Map.of("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8"));

		Byproduct.Executable.Allocate.Managed managed = new Byproduct.Executable.Allocate.Managed(
				Command.of("server"), Relative.root(), environment);
		environment.clear();

		assertEquals(1, managed.environment().size());
	}

	@Test
	void byproductContext_copiesOptions_andRequiresAPositiveMemoryBound() {
		Map<String, String> options = new HashMap<>(Map.of("motd", "hello"));

		Byproduct.Context.Default context = new Byproduct.Context.Default(PAPER_SERVER, PLATFORM, new MockRetention(),
				Relative.root(), WORLD, Optional.of(4L << 30), options);
		options.clear();

		assertEquals(Map.of("motd", "hello"), context.runtimeOptions());
		assertEquals(Optional.empty(),
				new Byproduct.Context.Default(PAPER_SERVER, PLATFORM, new MockRetention(), Relative.root(), WORLD)
						.maximumMemory());
		assertThrows(IllegalArgumentException.class, () -> new Byproduct.Context.Default(PAPER_SERVER, PLATFORM,
				new MockRetention(), Relative.root(), WORLD, Optional.of(0L), Map.of()));
	}

	@Test
	void productBuild_copiesContent_andServesOneDistribution() {
		Set<Byproduct> content = new HashSet<>(Set.of(ServerJar.of("server", Set.of()), Library.of("library")));

		Product.Build build = new Product.Build(BUILD, "1.21.1-130", Instant.EPOCH, 130L, content, Set.of());
		content.clear();

		assertEquals(2, build.content().size());
		assertSame(Java.Manager.Of.PAPER, build.distribution());
		assertEquals(Set.of(PAPER_SERVER), build.labels());
	}

	@Test
	void productBuild_rejectsByproductsSpanningDistributions() {
		ServerJar fabric = new ServerJar(file("fabric"), "Fabric", source("fabric.jar", bytes("f")),
				Set.of(FABRIC_SERVER), Set.of());
		Set<Byproduct> mixed = Set.of(ServerJar.of("server", Set.of()), fabric);

		assertThrows(IllegalArgumentException.class, () -> new Product.Build(BUILD, "1", Instant.EPOCH, 1L, mixed,
				Set.of()));
	}

	@Test
	void product_validatesItsBuilds_andSelectsTheLatest() {
		Product.Build build = new Product.Build(BUILD, "1.21.1-130", Instant.EPOCH, 130L,
				Set.of(ServerJar.of("server", Set.of())), Set.of());
		Coordinate.Project folia = new Coordinate.Project(PROJECT.providerId(), "folia");

		Paper paper = new Paper(PROJECT, "Paper", Java.Manager.Of.PAPER, Set.of(build));

		assertEquals(Optional.of(build), paper.latest());
		assertEquals(Optional.of(build), paper.latest(Set.of(BUILD.channel())));
		assertEquals(Optional.empty(), paper.latest(Set.of(PROJECT.channel("experimental"))));
		assertThrows(IllegalArgumentException.class, () -> new Paper(PROJECT, " ", Java.Manager.Of.PAPER, Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new Paper(PROJECT, "Paper", Java.Manager.Of.PURPUR, Set.of(build)));
		assertThrows(IllegalArgumentException.class,
				() -> new Paper(folia, "Folia", Java.Manager.Of.PAPER, Set.of(build)));
	}

	private static Artifact.Layout layout(Artifact artifact) throws IOException {
		Label label = artifact.labels().iterator().next();
		return label.distribution().layout(artifact, context(label, new MockRetention()));
	}

	private static String directory(Artifact artifact) {
		return artifact.artifactId().id().toString();
	}

	private static Artifact artifact(Class<? extends Artifact> family, Label label) {
		Describe source = source("content.bin", bytes("content"));
		Set<Label> labels = Set.of(label);
		Parity parity = Parity.UNKNOWN;
		Set<Artifact.Relation> none = Set.of();
		Coordinate.File coordinates = file(family.getSimpleName());
		if (family == Mod.class) {
			return new Mod.Default(coordinates, "Mod", source, labels, parity, none);
		} else if (family == Plugin.class) {
			return new Plugin.Default(coordinates, "Plugin", source, labels, parity, none);
		} else if (family == Datapack.class) {
			return new Datapack.Default(coordinates, "Datapack", source, labels, parity, none);
		} else if (family == Behaviourpack.class) {
			return new Behaviourpack.Default(coordinates, "Behaviour", source, labels, parity, none);
		} else if (family == Modpack.class) {
			return new Modpack.Default(coordinates, "Modpack", source, labels, parity, none);
		} else if (family == Resourcepack.Java.class) {
			return new Resourcepack.Java.Default(coordinates, "Resources", source, labels, parity, none);
		}
		return new Resourcepack.Bedrock.Default(coordinates, "Resources", source, labels, parity, none);
	}

	/**
	 * A server jar the executor runs with a provisioned Java runtime.
	 */
	record ServerJar(
			Coordinate.File coordinates,
			String byproductName,
			Describe source,
			Set<Label> labels,
			Set<Platform> platforms) implements Byproduct.Executable {

		ServerJar {
			labels = Set.copyOf(labels);
			platforms = Set.copyOf(platforms);
			Byproduct.validate(byproductName, labels);
		}

		static ServerJar of(String key, Set<Platform> platforms) {
			return new ServerJar(file(key), key, TestFixtures.source(key + ".jar", bytes(key)), Set.of(PAPER_SERVER), platforms);
		}

		@Override
		public Optional<Toolchain> toolchain() {
			return Optional.of(Toolchain.Java.atLeast(21));
		}

		@Override
		public Intermediate<Set<Image<?>>> install(Installer.Context context) {
			return context.repository()
					.capture(source)
					.map(captured -> Set.<Image<?>>of(Image.create(Relative.root(captured.fileName()), captured)));
		}

		@Override
		public Allocate allocate(Byproduct.Context context) {
			List<Command.Token> tokens = new ArrayList<>();
			context.maximumMemory().ifPresent(memory -> tokens.add(new Command.Option("-Xmx",
					new Command.Argument.Literal(String.valueOf(memory)), Command.Option.Style.JOINED)));
			tokens.add(new Command.Argument.Literal("-jar"));
			tokens.add(new Command.Argument.Location(Relative.root("server.jar")));
			tokens.add(new Command.Argument.Literal("--nogui"));
			return new Allocate.Managed(new Command(new Command.Argument.Binary(toolchain().orElseThrow()), tokens),
					Relative.root(), Map.of());
		}

		@Override
		public Deallocate deallocate(Byproduct.Context context) {
			return new Deallocate.Input("stop", Duration.ofSeconds(30));
		}
	}

	/**
	 * A library placed beside the server but never run itself.
	 */
	record Library(Coordinate.File coordinates, String byproductName, Describe source, Set<Label> labels)
			implements Byproduct.Nonexecutable {

		Library {
			labels = Set.copyOf(labels);
			Byproduct.validate(byproductName, labels);
		}

		static Library of(String key) {
			return new Library(file(key), key, TestFixtures.source(key + ".jar", bytes(key)), Set.of(PAPER_SERVER));
		}

		@Override
		public Intermediate<Set<Image<?>>> install(Installer.Context context) {
			return Intermediate.of(Set.of());
		}
	}

	/**
	 * A provider-owned distribution project.
	 */
	record Paper(Coordinate.Project coordinates, String projectName, Distribution distribution, Set<Build> builds)
			implements Product {

		Paper {
			builds = Set.copyOf(builds);
			Product.validate(coordinates, projectName, distribution, builds);
		}
	}
}
