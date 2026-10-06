package consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.addon.Addon;
import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Datapack;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.api.Query;
import cat.tabbie.sdk.api.Queryable;
import cat.tabbie.sdk.merchant.Installer;
import cat.tabbie.sdk.merchant.Provider;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.minecraft.Version;
import cat.tabbie.sdk.minecraft.distribution.Byproduct;
import cat.tabbie.sdk.minecraft.distribution.Distribution;
import cat.tabbie.sdk.minecraft.distribution.Java;
import cat.tabbie.sdk.minecraft.distribution.Product;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Relative;
import cat.tabbie.sdk.platform.Toolchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the SDK the way an external provider and core would, from outside
 * its packages and using only public API. Compiling this class is itself part
 * of the check.
 */
class ConsumerTest {

	private static final Label FABRIC_SERVER = Label.parse("java:fabric/1.21.1/server");

	private static final Label FABRIC_CLIENT = new Label(Version.interpret("Minecraft 1.21.1"), Java.Launcher.Of.FABRIC,
			Environment.CLIENT);

	private static final Label PAPER_SERVER = Label.parse("java:paper/1.21.1/server");

	private static final Platform LINUX = new Platform(Platform.Kernel.LINUX, Platform.Architecture.X_64);

	@TempDir
	Path scratch;

	@Test
	void addonProvider_resolvesAndInstallsContent_fromPersistedCoordinates() throws IOException {
		ModCatalog catalog = new ModCatalog();
		Retention retention = new Retention();
		Artifact.Context context = new Artifact.Context.Default(FABRIC_SERVER, LINUX, retention,
				Relative.root("instances", "survival"), Relative.world("world"));

		Addon found = catalog.query(new Query.Singular<>(Provider.Search.text("example"))).orElseThrow();
		Addon.Build latest = found.latest().orElseThrow();
		String persisted = latest.coordinates().canonical();
		Coordinate.Build reread = (Coordinate.Build) Coordinate.parse(persisted);
		Addon.Build resolved = collapse(catalog.resolve(reread)).orElseThrow();
		Set<Image<?>> images = collapse(Intermediate.all(resolved.content().stream()
				.map(artifact -> install(artifact, context))
				.toList())
				.map(sets -> sets.stream().flatMap(Set::stream).collect(Collectors.<Image<?>>toUnmodifiableSet())));

		Image.validate(images);
		assertTrue(catalog.owns(reread));
		assertEquals(Compatibility.SUPPORTED, resolved.compatibility(FABRIC_SERVER));
		assertEquals(Set.of(
				Relative.root("instances", "survival", "mods", "example.jar"),
				Relative.root("instances", "survival", "world", "datapacks",
						datapackDirectory(resolved), "pack.mcmeta")),
				images.stream()
						.map(image -> context.resolve(((Image.Target.File) image.target()).relative()))
						.collect(Collectors.toSet()));
	}

	@Test
	void distributionProvider_describesARunnableServer() throws IOException {
		ServerCatalog catalog = new ServerCatalog();
		Byproduct.Context context = new Byproduct.Context.Default(PAPER_SERVER, LINUX, new Retention(), Relative.root(),
				Relative.world("world"), Optional.of(2048L << 20), Map.of());

		Product paper = catalog.query(new Query.Singular<>(Provider.Search.of(catalog.project("paper")))).orElseThrow();
		Collection<Product.Build> page = collapse(catalog.builds(paper.coordinates(), Set.of(PAPER_SERVER), 1));
		Byproduct.Executable server = page.iterator().next().content().stream()
				.filter(Byproduct.Executable.class::isInstance)
				.map(Byproduct.Executable.class::cast)
				.findFirst()
				.orElseThrow();

		assertTrue(server.supports(PAPER_SERVER, LINUX));
		assertEquals(Java.Manager.Of.PAPER, paper.distribution());
		assertEquals(Optional.of(Toolchain.Java.atLeast(21)), server.toolchain());
		assertEquals(List.of("java", "-Xmx2048M", "-jar", "server.jar", "nogui"),
				server.allocate(context).command().render(new Command.Resolver() {

					@Override
					public Path path(Relative relative) {
						return relative.relativePath();
					}

					@Override
					public Path toolchain(Toolchain toolchain) {
						return Path.of("java");
					}
				}));
	}

	@Test
	void archiveEntries_canBeInspectedAndNamed_outsideTheSdk() throws IOException {
		Describe archive = describe("pack.zip", datapackZip());

		List<Archive.Entry> entries = collapse(Archive.DEFAULT.inspect(archive));
		Relative.World destination = Relative.world("datapacks").resolve("pack").resolve(Path.of("data"));

		assertEquals(List.of("pack.mcmeta"), entries.stream().map(Archive.Entry::path).toList());
		assertEquals(Relative.world("datapacks", "pack", "data"), destination);
	}

	private <T> T collapse(Intermediate<T> work) throws IOException {
		return work.collapse(new Intermediate.Step.Context.Default(scratch, Observer.none()));
	}

	private static Intermediate<Set<Image<?>>> install(Artifact artifact, Artifact.Context context) {
		try {
			return artifact.install(context);
		} catch (IOException exception) {
			return Intermediate.of(ignored -> {
				throw exception;
			});
		}
	}

	private static String datapackDirectory(Addon.Build build) {
		return build.content().stream()
				.filter(Datapack.class::isInstance)
				.findFirst()
				.orElseThrow()
				.artifactId()
				.id()
				.toString();
	}

	private static Describe describe(String fileName, byte[] content) {
		return new Describe() {

			@Override
			public Reference.Pending of() {
				return new Reference.Pending(fileName);
			}

			@Override
			public InputStream open() {
				return new ByteArrayInputStream(content);
			}
		};
	}

	private static byte[] datapackZip() throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
			zip.putNextEntry(new ZipEntry("pack.mcmeta"));
			zip.write("{\"pack\":{}}".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return buffer.toByteArray();
	}

	/**
	 * A provider-owned addon project.
	 */
	record Project(Coordinate.Project coordinates, String addonName, Set<Addon.Build> builds) implements Addon {

		Project {
			builds = Set.copyOf(builds);
			Addon.validate(coordinates, addonName, builds);
		}
	}

	/**
	 * An in-memory addon catalog with one project and one build of a mod and a
	 * datapack.
	 */
	static final class ModCatalog implements Provider<Addon, Addon.Build> {

		private final Project example;

		ModCatalog() {
			Coordinate.Build release = project("example").channel("release").build("1.0.0");
			Set<Label> labels = Set.of(FABRIC_SERVER, FABRIC_CLIENT);
			Mod mod = new Mod.Default(release.file("jar"), "Example", describe("example.jar",
					"jar".getBytes(StandardCharsets.UTF_8)), labels, Parity.SERVER_AND_REQUIRED_CLIENT, Set.of());
			Datapack datapack;
			try {
				datapack = new Datapack.Default(release.file("datapack"), "Example Data", describe("data.zip", datapackZip()),
						labels, Parity.ONLY_SERVER, Set.of(new Artifact.Relation.Required(mod.coordinates())));
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
			Addon.Build build = new Addon.Build(release, "1.0.0", Instant.EPOCH, 1L, Set.of(FABRIC_SERVER),
					Set.of(mod, datapack));
			example = new Project(project("example"), "Example", Set.of(build));
		}

		@Override
		public Identity<Provider<?, ?>> providerId() {
			return Identity.create("consumer:mods");
		}

		@Override
		public String providerName() {
			return "Mods";
		}

		@Override
		public Optional<Collection<Addon>> query(Query.Several<Search> query) {
			boolean matches = query.criteria().text().map("example"::equals).orElse(true);
			return Optional.of(matches ? List.of(example) : List.of());
		}

		@Override
		public Queryable<Search, Addon.Build> releases() {
			return query -> Optional.of(example.builds().stream()
					.filter(build -> query.criteria().identifier().map(id -> id.includes(build.coordinates())).orElse(true))
					.toList());
		}
	}

	/**
	 * A distribution project of Paper server builds.
	 */
	record Server(Coordinate.Project coordinates, String projectName, Distribution distribution,
			Set<Product.Build> builds) implements Product {

		Server {
			builds = Set.copyOf(builds);
			Product.validate(coordinates, projectName, distribution, builds);
		}
	}

	/**
	 * A runnable Paper server jar.
	 */
	record ServerJar(Coordinate.File coordinates, String byproductName, Describe source, Set<Label> labels)
			implements Byproduct.Executable {

		ServerJar {
			labels = Set.copyOf(labels);
			Byproduct.validate(byproductName, labels);
		}

		@Override
		public Optional<Toolchain> toolchain() {
			return Optional.of(Toolchain.Java.atLeast(21));
		}

		@Override
		public Intermediate<Set<Image<?>>> install(Installer.Context context) {
			return context.repository()
					.capture(source)
					.map(captured -> Set.<Image<?>>of(Image.create(Relative.root("server.jar"), captured)));
		}

		@Override
		public Allocate allocate(Byproduct.Context context) {
			List<Command.Token> memory = context.maximumMemory()
					.<List<Command.Token>>map(bytes -> List.of(new Command.Option("-Xmx",
							new Command.Argument.Literal((bytes >> 20) + "M"), Command.Option.Style.JOINED)))
					.orElse(List.of());
			Command command = new Command(new Command.Argument.Binary(toolchain().orElseThrow()), memory)
					.with(List.of(new Command.Argument.Literal("-jar"),
							new Command.Argument.Location(Relative.root("server.jar")),
							new Command.Argument.Literal("nogui")));
			return new Allocate.Managed(command, Relative.root(), Map.of());
		}

		@Override
		public Deallocate deallocate(Byproduct.Context context) {
			return new Deallocate.Input("stop", Duration.ofMinutes(1));
		}
	}

	/**
	 * An in-memory distribution catalog with one Paper build.
	 */
	static final class ServerCatalog implements Provider<Product, Product.Build> {

		private final Server paper;

		ServerCatalog() {
			Coordinate.Build release = project("paper").channel("default").build("1.21.1-130");
			ServerJar jar = new ServerJar(release.file("server"), "Paper", describe("paper.jar",
					"paper".getBytes(StandardCharsets.UTF_8)), Set.of(PAPER_SERVER));
			paper = new Server(project("paper"), "Paper", Java.Manager.Of.PAPER,
					Set.of(new Product.Build(release, "1.21.1-130", Instant.EPOCH, 130L, Set.of(jar), Set.of())));
		}

		@Override
		public Identity<Provider<?, ?>> providerId() {
			return Identity.create("consumer:servers");
		}

		@Override
		public String providerName() {
			return "Servers";
		}

		@Override
		public Optional<Collection<Product>> query(Query.Several<Search> query) {
			return Optional.of(List.of(paper));
		}

		@Override
		public Queryable<Search, Product.Build> releases() {
			return query -> Optional.of(List.copyOf(paper.builds()));
		}
	}

	/**
	 * An in-memory retention backend.
	 */
	static final class Retention implements Extract {

		private final Map<String, byte[]> retained = new HashMap<>();

		@Override
		public Intermediate<Reference.Captured> capture(Describe source,
				Observer<? super Reference.Captured, ? super Transfer> observer) {
			return Intermediate.of(context -> {
				ByteArrayOutputStream sink = new ByteArrayOutputStream();
				try (InputStream input = source.open()) {
					Reference.Captured captured = Extract.transfer(source.of(), input, sink, observer);
					retained.put(captured.sha256(), sink.toByteArray());
					return captured;
				}
			});
		}

		@Override
		public Describe retained(Reference.Captured reference) {
			return describe(reference.fileName(), retained.get(reference.sha256()));
		}
	}
}
