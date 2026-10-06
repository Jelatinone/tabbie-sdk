package cat.tabbie.sdk.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeTest {

	@Test
	void paths_holdCanonicalComponents_scopedToTheirMount() {
		Relative.Root root = Relative.root("mods", "a.jar");
		Relative.World world = Relative.world("mods", "a.jar");

		assertEquals(List.of("mods", "a.jar"), root.components());
		assertNotEquals(root, world);
		assertTrue(Relative.root().isMount());
		assertFalse(root.isMount());
	}

	@Test
	void components_mustEachBeOnePortableFilename() {
		for (String invalid : new String[] { "", " ", ".", "..", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b",
				"a>b", "a|b", "a\u0000b", "a\tb" }) {
			assertThrows(IllegalArgumentException.class, () -> Relative.root(invalid), invalid);
			assertThrows(IllegalArgumentException.class, () -> Relative.world("ok", invalid), invalid);
		}
	}

	@Test
	void components_areImmutableCopies() {
		List<String> components = new ArrayList<>(List.of("mods"));

		Relative.Root root = new Relative.Root(components);
		components.add("later");

		assertEquals(List.of("mods"), root.components());
		assertThrows(UnsupportedOperationException.class, () -> root.components().add("x"));
	}

	@Test
	void resolve_keepsTheMountKind_forEveryOverload() {
		Relative.Root root = Relative.root("config");
		Relative.World world = Relative.world("datapacks");

		Relative.Root rootByName = root.resolve("a", "b.txt");
		Relative.Root rootByList = root.resolve(List.of("a", "b.txt"));
		Relative.Root rootByPath = root.resolve(Path.of("a", "b.txt"));
		Relative.World worldByName = world.resolve("pack", "pack.mcmeta");
		Relative.World worldByList = world.resolve(List.of("pack", "pack.mcmeta"));
		Relative.World worldByPath = world.resolve(Path.of("pack", "pack.mcmeta"));

		assertEquals(Relative.root("config", "a", "b.txt"), rootByName);
		assertEquals(rootByName, rootByList);
		assertEquals(rootByName, rootByPath);
		assertEquals(Relative.world("datapacks", "pack", "pack.mcmeta"), worldByName);
		assertEquals(worldByName, worldByList);
		assertEquals(worldByName, worldByPath);
	}

	@Test
	void resolveOfANativePath_normalizesDotSegments_butNeverEscapes() {
		Relative.Root root = Relative.root("config");

		assertEquals(Relative.root("config", "b.txt"), root.resolve(Path.of("a", "..", "b.txt")));
		assertEquals(root, root.resolve(Path.of("")));
		assertThrows(IllegalArgumentException.class, () -> root.resolve(Path.of("..", "escape.txt")));
		assertThrows(IllegalArgumentException.class, () -> root.resolve(Path.of("a", "..", "..", "escape.txt")));
		assertThrows(IllegalArgumentException.class, () -> root.resolve(Path.of("").toAbsolutePath()));
	}

	@Test
	void encoded_roundTripsThroughDecode() {
		Relative.World world = Relative.world("datapacks", "pack name", "data.json");

		List<String> decoded = Relative.decode(world.encoded());

		assertEquals("datapacks/pack name/data.json", world.encoded());
		assertEquals(world.components(), decoded);
		assertEquals(List.of(), Relative.decode(""));
		assertEquals("", Relative.root().encoded());
	}

	@Test
	void decode_rejectsNonCanonicalEncodings() {
		for (String invalid : new String[] { "/a", "a/", "a//b", "./a", "a/../b", "a\\b" }) {
			assertThrows(IllegalArgumentException.class, () -> Relative.decode(invalid), invalid);
		}
	}

	@Test
	void parent_andWithin_describeContainmentUnderTheSameMount() {
		Relative.Root file = Relative.root("mods", "nested", "a.jar");

		assertEquals(Relative.root("mods", "nested"), file.parent());
		assertNull(Relative.root("a.jar").parent());
		assertNull(Relative.root().parent());
		assertTrue(file.within(Relative.root("mods")));
		assertTrue(file.within(Relative.root()));
		assertFalse(file.within(file));
		assertFalse(file.within(Relative.world("mods")));
		assertFalse(Relative.root("modsx", "a.jar").within(Relative.root("mods")));
	}

	@Test
	void relativePath_convertsToANativeRelativePath() {
		assertEquals(Path.of("mods", "a.jar"), Relative.root("mods", "a.jar").relativePath());
		assertEquals(Path.of(""), Relative.world().relativePath());
		assertFalse(Relative.root("mods").relativePath().isAbsolute());
	}
}
