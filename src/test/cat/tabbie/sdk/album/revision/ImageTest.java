package cat.tabbie.sdk.album.revision;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.platform.Package.Manager;
import cat.tabbie.sdk.platform.Relative;

import static cat.tabbie.sdk.TestFixtures.bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageTest {

	private static final Relative.Root JAR = Relative.root("mods", "example.jar");

	private static final Reference.Captured FIRST = Reference.of("example.jar", bytes("first"));

	private static final Reference.Captured SECOND = Reference.of("example.jar", bytes("second"));

	private static final Reference.Captured THIRD = Reference.of("example.jar", bytes("third"));

	private static final Package JAVA = new Package("java-runtime",
			Map.of(Manager.APT, new Package.Specification("openjdk-21-jre-headless")));

	@Test
	void fileFactories_describeExistenceChanges_andPreimagesSwapThem() {
		Image<Image.File> created = Image.create(JAR, FIRST);
		Image<Image.File> replaced = Image.replace(JAR, FIRST, SECOND);
		Image<Image.File> deleted = Image.delete(JAR, FIRST);

		assertInstanceOf(Image.File.Absent.class, created.before());
		assertEquals(new Image.File.Present(FIRST), created.after());
		assertEquals(new Image.File.Present(SECOND), replaced.after());
		assertInstanceOf(Image.File.Absent.class, deleted.after());
		assertEquals(deleted, created.preimage());
		assertEquals(Image.replace(JAR, SECOND, FIRST), replaced.preimage());
		assertEquals(new Image.Target.File(JAR), created.target());
	}

	@Test
	void installation_rejectsAbsenceOnBothSides_andTargetsBeneathTheMount() {
		assertThrows(IllegalArgumentException.class, () -> new Image.Installation(
				new Image.File.Absent(), new Image.File.Absent(), new Image.Target.File(JAR)));
		assertThrows(IllegalArgumentException.class, () -> new Image.Target.File(Relative.root()));
		assertThrows(IllegalArgumentException.class, () -> new Image.Target.File(Relative.world()));
	}

	@Test
	void configure_describesSelectedRegions_andEqualTextAssertsOnlyExistence() {
		Relative.Root path = Relative.root("config", "example.properties");

		Image<Image.Text> changed = Image.configure(path, "a=1\nb=2\n", "a=1\nb=3\n");
		Image<Image.Text> unchanged = Image.configure(path, "a=1\n", "a=1\n");

		assertEquals("a=1\nb=2\n", changed.before().fragments().getFirst().text());
		assertEquals("a=1\nb=3\n", changed.after().fragments().getFirst().text());
		assertEquals(changed.before(), changed.preimage().after());
		assertEquals(List.of(), unchanged.before().fragments());
		assertEquals(List.of(), unchanged.after().fragments());
	}

	@Test
	void configure_pairsExplicitFragmentStates() {
		Relative.Root path = Relative.root("config.txt");
		Image.Text before = new Image.Text(List.of(new Chunk.Fragment(Chunk.linesOf("x\n"), new Chunk.Range(2, 1))));
		Image.Text after = new Image.Text(List.of(new Chunk.Fragment(Chunk.linesOf("y\n"), new Chunk.Range(2, 1))));

		Image<Image.Text> image = Image.configure(path, before, after);

		assertEquals(before, image.before());
		assertEquals(after, image.after());
	}

	@Test
	void provision_carriesOptions_andRejectsAbsenceOnBothSidesOrDisjointManagers() {
		Map<Manager, List<String>> install = new HashMap<>(Map.of(Manager.APT, List.of("--no-install-recommends")));
		Map<Manager, List<String>> undo = Map.of(Manager.APT, List.of("--purge"));

		Image<Image.Claim> image = Image.provision(JAVA, install, undo);
		install.put(Manager.DNF, List.of());

		assertEquals(new Image.Claim.Present(Map.of(Manager.APT, List.of("--no-install-recommends"))), image.after());
		assertEquals(new Image.Claim.Absent(undo), image.before());
		assertEquals(Image.deprovision(JAVA, undo, Map.of(Manager.APT, List.of("--no-install-recommends"))),
				image.preimage());
		assertThrows(IllegalArgumentException.class, () -> new Image.Provision(
				new Image.Claim.Absent(), new Image.Claim.Absent(), new Image.Target.Pack(JAVA)));
		assertThrows(IllegalArgumentException.class, () -> Image.provision(JAVA,
				Map.of(Manager.APT, List.of()), Map.of(Manager.DNF, List.of())));
	}

	@Test
	void claimOptions_areImmutableCopies() {
		List<String> options = new ArrayList<>(List.of("--yes"));

		Image.Claim.Present claim = new Image.Claim.Present(Map.of(Manager.APT, options));
		options.add("--later");

		assertEquals(List.of("--yes"), claim.installOption().get(Manager.APT));
		assertThrows(UnsupportedOperationException.class, () -> claim.installOption().put(Manager.DNF, List.of()));
	}

	@Test
	void validate_requiresUniqueTargets_andNoFileUsedAsADirectory() {
		Image<?> jar = Image.create(JAR, FIRST);
		Image<?> config = Image.configure(JAR, "a\n", "b\n");
		Image<?> nested = Image.create(JAR.resolve("inner.txt"), SECOND);
		Image<?> sameNameInWorld = Image.create(Relative.world("mods", "example.jar"), SECOND);
		Image<?> claim = Image.provision(JAVA);

		Image.validate(List.of(jar, sameNameInWorld, claim));

		assertThrows(IllegalArgumentException.class, () -> Image.validate(List.of(jar, config)));
		assertThrows(IllegalArgumentException.class, () -> Image.validate(List.of(jar, nested)));
		assertThrows(IllegalArgumentException.class, () -> Image.validate(List.of(claim, Image.deprovision(JAVA))));
	}

	@Test
	void preimageOfASet_undoesEveryImage() {
		Set<Image<?>> images = Set.of(Image.create(JAR, FIRST), Image.provision(JAVA));

		Set<Image<?>> undone = Image.preimage(images);

		assertEquals(Set.of(Image.delete(JAR, FIRST), Image.deprovision(JAVA)), undone);
	}

	@Test
	void compose_foldsWholeFileChanges_fromFirstBeforeToSecondAfter() {
		Optional<Image<?>> createThenReplace = Image.compose(Image.create(JAR, FIRST), Image.replace(JAR, FIRST, SECOND));
		Optional<Image<?>> replaceTwice = Image.compose(Image.replace(JAR, FIRST, SECOND),
				Image.replace(JAR, SECOND, THIRD));
		Optional<Image<?>> createThenDelete = Image.compose(Image.create(JAR, FIRST), Image.delete(JAR, FIRST));

		assertEquals(Optional.of(Image.create(JAR, SECOND)), createThenReplace);
		assertEquals(Optional.of(Image.replace(JAR, FIRST, THIRD)), replaceTwice);
		assertEquals(Optional.empty(), createThenDelete);
	}

	@Test
	void compose_rejectsOtherTargets_discontinuity_andMixedKinds() {
		Image<?> created = Image.create(JAR, FIRST);

		assertThrows(IllegalArgumentException.class,
				() -> Image.compose(created, Image.delete(Relative.root("other.jar"), FIRST)));
		assertThrows(IllegalArgumentException.class, () -> Image.compose(created, Image.delete(JAR, SECOND)));
		assertThrows(Image.IrreducibleException.class, () -> Image.compose(created, Image.configure(JAR, "a\n", "b\n")));
	}

	@Test
	void compose_cancelsAClaimTakenAndReleased_evenWithDifferentOptions() {
		Image<Image.Claim> claimed = Image.provision(JAVA);
		Image<Image.Claim> released = Image.deprovision(JAVA, Map.of(Manager.APT, List.of("--purge")), Map.of());

		Optional<Image<?>> net = Image.compose(claimed, released);

		assertEquals(Optional.empty(), net);
	}

	@Test
	void compose_foldsTextChanges_andCancelsAnInverse() {
		Relative.Root path = Relative.root("config.txt");
		Image<Image.Text> first = Image.configure(path, "a\nb\nc\n", "a\nB\nc\n");
		Image<Image.Text> second = Image.configure(path, "a\nB\nc\n", "a\nB\nC\n");

		Image<?> composed = Image.compose(first, second).orElseThrow();
		Optional<Image<?>> cancelled = Image.compose(first, first.preimage());

		assertEquals("a\nB\nC\n", assertInstanceOf(Image.Configuration.class, composed).after().fragments().getFirst()
				.text());
		assertEquals(Optional.empty(), cancelled);
	}

	@Test
	void reduce_foldsSequentialSets_andDropsTargetsThatCancel() {
		List<Set<Image<?>>> changes = List.of(
				Set.of(Image.create(JAR, FIRST), Image.provision(JAVA)),
				Set.of(Image.replace(JAR, FIRST, SECOND)),
				Set.of(Image.deprovision(JAVA)));

		Set<Image<?>> net = Image.reduce(changes);

		assertEquals(Set.of(Image.create(JAR, SECOND)), net);
	}

	@Test
	void reduce_rejectsInvalidSets_andOrderDependentNetChanges() {
		Relative.Root directory = Relative.root("mods", "example");
		List<Set<Image<?>>> invalid = List.of(Set.of(Image.create(JAR, FIRST), Image.configure(JAR, "a\n", "b\n")));
		List<Set<Image<?>>> fileBecomesDirectory = List.of(
				Set.of(Image.delete(directory, FIRST)),
				Set.of(Image.create(directory.resolve("inner.jar"), SECOND)));

		assertThrows(IllegalArgumentException.class, () -> Image.reduce(invalid));
		assertThrows(IllegalArgumentException.class, () -> Image.reduce(fileBecomesDirectory));
	}

	@Test
	void text_rejectsOverlappingFragments() {
		List<Chunk.Fragment> overlapping = List.of(
				new Chunk.Fragment(Chunk.linesOf("a\nb\n"), new Chunk.Range(0, 2)),
				new Chunk.Fragment(Chunk.linesOf("c\n"), new Chunk.Range(1, 1)));

		assertThrows(IllegalArgumentException.class, () -> new Image.Text(overlapping));
		assertTrue(new Image.Text(List.of()).fragments().isEmpty());
	}
}
