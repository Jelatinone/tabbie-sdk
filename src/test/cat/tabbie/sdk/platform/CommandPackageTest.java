package cat.tabbie.sdk.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cat.tabbie.sdk.platform.Command.Argument;
import cat.tabbie.sdk.platform.Command.Option;
import cat.tabbie.sdk.platform.Package.Manager;
import cat.tabbie.sdk.platform.Package.Specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPackageTest {

	private static final Platform LINUX = new Platform(Platform.Kernel.LINUX, Platform.Architecture.X_64);

	private static final Command.Resolver RESOLVER = new Command.Resolver() {

		@Override
		public Path path(Relative relative) {
			return Path.of(relative instanceof Relative.World ? "world" : "root").resolve(relative.relativePath());
		}

		@Override
		public Path toolchain(Toolchain toolchain) {
			return Path.of("toolchains", toolchain.canonical().replace(':', '-'), "java");
		}
	};

	private static final Package JAVA = new Package("java-runtime", Map.of(
			Manager.APT, new Specification("openjdk-21-jre-headless", "21.0.4+7-1"),
			Manager.DNF, new Specification("java-21-openjdk-headless"),
			Manager.WINGET, new Specification("EclipseAdoptium.Temurin.21.JRE", "21.0.4.7")));

	@Test
	void render_bindsEveryTokenKind_inOrder() {
		Command command = new Command(new Argument.Binary(Toolchain.Java.atLeast(21)), List.of(
				new Option("-Xmx", new Argument.Literal("4G"), Option.Style.JOINED),
				new Option("-Dlog4j.configurationFile", new Argument.Location(Relative.root("log4j2.xml")),
						Option.Style.EQUALS),
				new Argument.Literal("-jar"),
				new Argument.Location(Relative.root("server.jar")),
				new Option("--world", new Argument.Location(Relative.world()), Option.Style.SEPARATE),
				new Argument.Literal("")));

		List<String> argv = command.render(RESOLVER);

		assertEquals(List.of(
				Path.of("toolchains", "java-21+", "java").toString(),
				"-Xmx4G",
				"-Dlog4j.configurationFile=" + Path.of("root", "log4j2.xml"),
				"-jar",
				Path.of("root", "server.jar").toString(),
				"--world",
				Path.of("world").toString(),
				""), argv);
		assertThrows(UnsupportedOperationException.class, () -> argv.add("more"));
	}

	@Test
	void command_copiesTokens_andAppendsWithoutMutating() {
		List<Command.Token> tokens = new ArrayList<>(Argument.literals("a"));

		Command command = new Command(new Argument.Literal("tool"), tokens);
		tokens.clear();
		Command extended = command.with(Argument.literals("b", "c"));

		assertEquals(List.of("tool", "a"), command.render(RESOLVER));
		assertEquals(List.of("tool", "a", "b", "c"), extended.render(RESOLVER));
	}

	@Test
	void command_joinsFixedArguments_callerOptions_andTrailingArguments() {
		Command command = Command.of("tool", List.of("install"), List.of("--quiet"), List.of("package"));

		assertEquals(List.of("tool", "install", "--quiet", "package"), command.render(RESOLVER));
		assertEquals(Command.of("tool", "install", "--quiet", "package"), command);
	}

	@Test
	void command_rejectsABlankProgram_andOptionsWithoutNames() {
		assertThrows(IllegalArgumentException.class, () -> Command.of(" "));
		assertThrows(IllegalArgumentException.class,
				() -> new Option(" ", new Argument.Literal("x"), Option.Style.SEPARATE));
		assertThrows(NullPointerException.class, () -> new Argument.Literal(null));
	}

	@Test
	void package_requiresAWhitespaceFreeName_andASpecification() {
		Map<Manager, Specification> one = Map.of(Manager.APT, new Specification("curl"));

		assertThrows(IllegalArgumentException.class, () -> new Package(" ", one));
		assertThrows(IllegalArgumentException.class, () -> new Package("java runtime", one));
		assertThrows(IllegalArgumentException.class, () -> new Package("curl", Map.of()));
		assertThrows(IllegalArgumentException.class, () -> new Specification("open jdk"));
		assertThrows(IllegalArgumentException.class, () -> new Specification("openjdk", " "));
		assertThrows(IllegalArgumentException.class, () -> new Specification("openjdk", "21 lts"));
	}

	@Test
	void package_copiesItsSpecifications() {
		Map<Manager, Specification> specifications = new HashMap<>(Map.of(Manager.APT, new Specification("curl")));

		Package curl = new Package("curl", specifications);
		specifications.put(Manager.DNF, new Specification("curl"));

		assertEquals(1, curl.specifications().size());
	}

	@Test
	void resolve_prefersTheHostsFirstCarryingManager() {
		Host fedora = new Host(LINUX, List.of(Manager.HOMEBREW, Manager.DNF, Manager.APT));
		Host arch = new Host(LINUX, List.of(Manager.PACMAN));

		Optional<Package.Resolved> resolved = JAVA.resolve(fedora);

		assertEquals(Optional.of(new Package.Resolved(Manager.DNF, new Specification("java-21-openjdk-headless"))),
				resolved);
		assertEquals(Optional.empty(), JAVA.resolve(arch));
		assertThrows(IllegalStateException.class, () -> JAVA.install(arch, List.of()));
	}

	@Test
	void install_spellsEachManagersNonInteractiveCommand() {
		Host debian = new Host(LINUX, List.of(Manager.APT));
		Host windows = new Host(new Platform(Platform.Kernel.WINDOWS, Platform.Architecture.X_64),
				List.of(Manager.WINGET));
		Host fedora = new Host(LINUX, List.of(Manager.DNF));

		assertEquals(List.of("apt-get", "install", "--yes", "--quiet", "--no-install-recommends",
				"openjdk-21-jre-headless=21.0.4+7-1"), JAVA.install(debian, List.of("--no-install-recommends"))
						.render(RESOLVER));
		assertEquals(List.of("winget", "install", "--silent", "--disable-interactivity", "--accept-package-agreements",
				"--accept-source-agreements", "--id", "EclipseAdoptium.Temurin.21.JRE", "--exact", "--version",
				"21.0.4.7"), JAVA.install(windows, List.of()).render(RESOLVER));
		assertEquals(List.of("dnf", "install", "--assumeyes", "--quiet", "java-21-openjdk-headless"),
				JAVA.install(fedora, List.of()).render(RESOLVER));
	}

	@Test
	void installCommands_pinVersions_onlyWhereTheManagerCan() {
		Specification pinned = new Specification("openjdk", "21");

		assertEquals(List.of("apt-get", "install", "--yes", "--quiet", "openjdk=21"),
				Manager.APT.install(pinned, List.of()).render(RESOLVER));
		assertEquals(List.of("dnf", "install", "--assumeyes", "--quiet", "openjdk-21"),
				Manager.DNF.install(pinned, List.of()).render(RESOLVER));
		assertEquals(List.of("pacman", "--sync", "--needed", "--noconfirm", "openjdk"),
				Manager.PACMAN.install(pinned, List.of()).render(RESOLVER));
		assertEquals(List.of("brew", "install", "--quiet", "openjdk"),
				Manager.HOMEBREW.install(pinned, List.of()).render(RESOLVER));
	}

	@Test
	void verifyAndUninstall_useTheManagerThatCarriesThePackage() {
		Host both = new Host(LINUX, List.of(Manager.DNF, Manager.APT, Manager.PACMAN));

		Map<Manager, Command> checks = JAVA.verify(both);

		assertEquals(List.of(Manager.DNF, Manager.APT), List.copyOf(checks.keySet()));
		assertEquals(List.of("dpkg-query", "--show", "openjdk-21-jre-headless"), checks.get(Manager.APT).render(RESOLVER));
		assertEquals(List.of("rpm", "--query", "java-21-openjdk-headless"), checks.get(Manager.DNF).render(RESOLVER));
		assertEquals(List.of("apt-get", "remove", "--yes", "--quiet", "--purge", "openjdk-21-jre-headless"),
				JAVA.uninstall(Manager.APT, List.of("--purge")).render(RESOLVER));
		assertThrows(IllegalArgumentException.class, () -> JAVA.uninstall(Manager.PACMAN, List.of()));
	}

	@Test
	void managers_haveStableIdentifiers_probes_andPrivilegeRequirements() {
		for (Manager manager : Manager.values()) {
			assertEquals(Optional.of(manager), Manager.of(manager.id()));
			assertEquals(List.of(manager.program(), "--version"), manager.probe().render(RESOLVER));
		}
		assertEquals(Manager.values().length, Arrays.stream(Manager.values()).map(Manager::id).distinct().count());
		assertEquals(Optional.empty(), Manager.of("APT"));
		assertTrue(Manager.APT.privileged() && Manager.DNF.privileged() && Manager.PACMAN.privileged());
		assertTrue(!Manager.HOMEBREW.privileged() && !Manager.WINGET.privileged());
	}

	@Test
	void host_listsEachManagerOnce() {
		List<Manager> managers = new ArrayList<>(List.of(Manager.APT));

		Host host = new Host(LINUX, managers);
		managers.add(Manager.DNF);

		assertEquals(List.of(Manager.APT), host.managers());
		assertThrows(IllegalArgumentException.class, () -> new Host(LINUX, List.of(Manager.APT, Manager.APT)));
	}
}
