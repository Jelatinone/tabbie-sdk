package cat.tabbie.sdk.platform;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformToolchainTest {

	@Test
	void local_recognizesCommonKernelAndArchitectureNames() {
		assertEquals(new Platform(Platform.Kernel.WINDOWS, Platform.Architecture.X_64), local("Windows 11", "amd64"));
		assertEquals(new Platform(Platform.Kernel.MAC, Platform.Architecture.ARM_64), local("Mac OS X", "aarch64"));
		assertEquals(new Platform(Platform.Kernel.MAC, Platform.Architecture.X_64), local("Darwin", "x86_64"));
		assertEquals(new Platform(Platform.Kernel.LINUX, Platform.Architecture.ARM_64), local("Linux", "arm64"));
	}

	@Test
	void local_rejectsUnrecognizedMachines() {
		assertThrows(IllegalStateException.class, () -> local("Plan 9", "amd64"));
		assertThrows(IllegalStateException.class, () -> local("Linux", "riscv64"));
	}

	@Test
	void local_describesTheRunningMachine() {
		Platform platform = Platform.local();

		assertEquals(platform, Platform.local());
	}

	@Test
	void javaToolchain_acceptsAnInclusiveFeatureRange() {
		Toolchain.Java modern = Toolchain.Java.atLeast(21);
		Toolchain.Java legacy = Toolchain.Java.exactly(8);
		Toolchain.Java bounded = new Toolchain.Java(17, OptionalInt.of(21));

		assertTrue(modern.accepts(21) && modern.accepts(25));
		assertFalse(modern.accepts(17));
		assertTrue(legacy.accepts(8));
		assertFalse(legacy.accepts(9));
		assertTrue(bounded.accepts(17) && bounded.accepts(21));
		assertFalse(bounded.accepts(16) || bounded.accepts(22));
	}

	@Test
	void javaToolchain_rejectsEmptyOrNonPositiveRanges() {
		assertThrows(IllegalArgumentException.class, () -> Toolchain.Java.atLeast(0));
		assertThrows(IllegalArgumentException.class, () -> new Toolchain.Java(21, OptionalInt.of(17)));
	}

	@Test
	void canonical_roundTripsThroughParse() {
		for (Toolchain toolchain : new Toolchain[] { Toolchain.Java.atLeast(21), Toolchain.Java.exactly(8),
				new Toolchain.Java(17, OptionalInt.of(21)) }) {
			assertEquals(toolchain, Toolchain.parse(toolchain.canonical()));
		}
		assertEquals("java:21+", Toolchain.Java.atLeast(21).canonical());
		assertEquals("java:8-8", Toolchain.Java.exactly(8).canonical());
	}

	@Test
	void parse_acceptsOnlyCanonicalText() {
		for (String invalid : new String[] { "java:21", "java:021+", "java:0+", "java:21-17", "java: 21+", "jdk:21+",
				"java:21+-25" }) {
			assertThrows(IllegalArgumentException.class, () -> Toolchain.parse(invalid), invalid);
		}
	}

	/**
	 * Describes a machine by temporarily substituting the JVM's system
	 * properties.
	 */
	private static Platform local(String osName, String osArch) {
		String previousName = System.getProperty("os.name");
		String previousArch = System.getProperty("os.arch");
		try {
			System.setProperty("os.name", osName);
			System.setProperty("os.arch", osArch);
			return Platform.local();
		} finally {
			System.setProperty("os.name", previousName);
			System.setProperty("os.arch", previousArch);
		}
	}
}
