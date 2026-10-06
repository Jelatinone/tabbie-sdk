package cat.tabbie.sdk.album.revision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cat.tabbie.sdk.api.Observer;

import static cat.tabbie.sdk.TestFixtures.collapse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntermediateTest {

	@TempDir
	Path scratch;

	@Test
	void construction_runsNothing_untilCollapse_andCollapseRepeatsEffects() throws IOException {
		AtomicInteger runs = new AtomicInteger();

		Intermediate<Integer> work = Intermediate.of(context -> runs.incrementAndGet())
				.map(count -> count * 10)
				.flatMap(count -> Intermediate.of(count + 1))
				.then(Intermediate.of(context -> runs.get()));

		assertEquals(0, runs.get());
		assertEquals(1, collapse(work, scratch));
		assertEquals(2, collapse(work, scratch));
	}

	@Test
	void effects_receiveTheCollapsingContext() throws IOException {
		Intermediate.Step.Context context = new Intermediate.Step.Context.Default(scratch, Observer.none());

		Path seen = Intermediate.of(Intermediate.Step.Context::scratch)
				.flatMap(first -> Intermediate.of(Intermediate.Step.Context::scratch))
				.collapse(context);

		assertSame(scratch, seen);
	}

	@Test
	void then_runsTheNextEffectAfterThisOne() throws IOException {
		List<String> order = new ArrayList<>();

		Intermediate<String> work = Intermediate.of(context -> order.add("first"))
				.then(context -> {
					order.add("second");
					return "done";
				});

		assertEquals("done", collapse(work, scratch));
		assertEquals(List.of("first", "second"), order);
	}

	@Test
	void groupAndAll_collapseInOrder_intoImmutableLists() throws IOException {
		List<String> order = new ArrayList<>();
		List<Intermediate<String>> elements = List.of(
				Intermediate.of(context -> record(order, "a")),
				Intermediate.of(context -> record(order, "b")));

		List<String> grouped = collapse(Intermediate.group(elements), scratch);
		List<String> all = collapse(Intermediate.all(elements), scratch);

		assertEquals(List.of("a", "b"), grouped);
		assertEquals(List.of("a", "b"), all);
		assertEquals(List.of("a", "b", "a", "b"), order);
		assertThrows(UnsupportedOperationException.class, () -> grouped.add("c"));
	}

	@Test
	void all_stopsAtTheFirstFailure() {
		AtomicInteger later = new AtomicInteger();
		List<Intermediate<String>> elements = List.of(
				Intermediate.of(context -> {
					throw new IOException("first fails");
				}),
				Intermediate.of(context -> String.valueOf(later.incrementAndGet())));

		assertThrows(IOException.class, () -> collapse(Intermediate.all(elements), scratch));
		assertEquals(0, later.get());
	}

	@Test
	void any_returnsTheFirstSuccess_withoutTryingLaterAlternatives() throws IOException {
		AtomicInteger later = new AtomicInteger();
		List<Intermediate<String>> alternatives = List.of(
				Intermediate.of(context -> {
					throw new IOException("mirror down");
				}),
				Intermediate.of("mirror"),
				Intermediate.of(context -> String.valueOf(later.incrementAndGet())));

		String result = collapse(Intermediate.any(alternatives), scratch);

		assertEquals("mirror", result);
		assertEquals(0, later.get());
	}

	@Test
	void any_throwsTheFirstFailure_withLaterFailuresSuppressed() {
		IOException first = new IOException("first");
		IOException second = new IOException("second");
		List<Intermediate<String>> alternatives = List.of(
				Intermediate.of(context -> {
					throw first;
				}),
				Intermediate.of(context -> {
					throw second;
				}));

		IOException thrown = assertThrows(IOException.class, () -> collapse(Intermediate.any(alternatives), scratch));

		assertSame(first, thrown);
		assertEquals(List.of(second), List.of(thrown.getSuppressed()));
	}

	@Test
	void any_requiresAnAlternative() {
		assertThrows(IllegalArgumentException.class, () -> Intermediate.any(List.of()));
	}

	@Test
	void step_namesItselfAfterItsEnclosingType() {
		Intermediate.Step<String> step = new Probe();

		assertEquals("IntermediateTest:Probe", step.name());
		assertEquals("Step:Effect", new Intermediate.Step.Effect<>(context -> "x").name());
	}

	@Test
	void stepContext_defaultsToADiscardingObserver() {
		Intermediate.Step.Context context = () -> scratch;

		assertSame(Observer.none(), context.observer());
	}

	private static String record(List<String> order, String value) {
		order.add(value);
		return value;
	}

	record Probe() implements Intermediate.Step<String> {

		@Override
		public String collapse(Context context) {
			return "probe";
		}
	}
}
