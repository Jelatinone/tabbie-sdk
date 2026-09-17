package cat.tabbie.sdk.album;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import cat.tabbie.sdk.album.Chunk.Change;
import cat.tabbie.sdk.album.Chunk.Change.Line;
import cat.tabbie.sdk.album.Chunk.Change.Revision;
import cat.tabbie.sdk.album.Chunk.Change.Sequence;
import cat.tabbie.sdk.album.Chunk.Fragment;
import cat.tabbie.sdk.album.Chunk.Range;

class ChunkTest {

	@ParameterizedTest
	@ValueSource(strings = { "", "\n", "\r", "\r\n", "\n\n", "last", "a\nb\r\nc\rd", "\r\n\r\n", "猫\n🐈" })
	void reconstructsExactText(String text) {
		List<Line> lines = Chunk.linesOf(text);
		assertEquals(text, new Fragment(lines, new Range(7, lines.size())).text());
	}

	@Test
	void splitsOnlyActualLinesAndPreservesEndings() {
		assertEquals(List.of(), Chunk.linesOf(""));
		assertEquals(List.of(new Line("", Sequence.LF)), Chunk.linesOf("\n"));
		assertEquals(List.of(new Line("a", Sequence.CRLF), new Line("b", Sequence.CR),
				new Line("c", Sequence.LF), new Line("d", Sequence.NONE)), Chunk.linesOf("a\r\nb\rc\nd"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "a\nb", "a\rb", "a\r\nb" })
	void rejectsNewlinesInsideLineText(String text) {
		assertThrows(IllegalArgumentException.class, () -> new Line(text, Sequence.LF));
	}

	@Test
	void validatesRangeWithoutIntegerOverflow() {
		assertThrows(IllegalArgumentException.class, () -> new Range(-1, 0));
		assertThrows(IllegalArgumentException.class, () -> new Range(0, -1));
		assertThrows(IllegalArgumentException.class, () -> new Range(Integer.MAX_VALUE, 1));
		assertThrows(IllegalArgumentException.class, () -> new Range(1, Integer.MAX_VALUE));
		assertThrows(IllegalArgumentException.class, () -> new Range(Integer.MAX_VALUE, Integer.MAX_VALUE));
		assertEquals(Integer.MAX_VALUE, new Range(Integer.MAX_VALUE, 0).end());
		assertEquals(Integer.MAX_VALUE, new Range(1, Integer.MAX_VALUE - 1).end());
	}

	@Test
	void validatesFragmentCountsAndTerminalLines() {
		assertThrows(IllegalArgumentException.class, () -> new Fragment(Chunk.linesOf("a\n"), new Range(0, 2)));
		assertThrows(IllegalArgumentException.class, () -> new Fragment(
				List.of(new Line("a", Sequence.NONE), new Line("b", Sequence.LF)), new Range(0, 2)));
		assertEquals("a\nb", new Fragment(Chunk.linesOf("a\nb"), new Range(0, 2)).text());
	}

	@Test
	void validatesConsumptionOnEachSide() {
		List<Change> changes = List.of(change("context\n", Revision.CONTEXT),
				change("old\n", Revision.DELETION), change("new", Revision.INSERTION));
		Chunk chunk = new Chunk(changes, new Range(4, 2), new Range(5, 2));
		assertEquals("context\nold\n", chunk.before().text());
		assertEquals("context\nnew", chunk.after().text());
		assertThrows(IllegalArgumentException.class, () -> new Chunk(changes, new Range(4, 1), new Range(5, 2)));
		assertThrows(IllegalArgumentException.class, () -> new Chunk(changes, new Range(4, 2), new Range(5, 3)));
		assertThrows(IllegalArgumentException.class, () -> new Chunk(
				List.of(change("last", Revision.CONTEXT), change("extra\n", Revision.INSERTION)),
				new Range(0, 1), new Range(0, 2)));
	}

	@Test
	void allowsEmptyAndContextOnlyManualChunks() {
		Chunk empty = new Chunk(List.of(), new Range(8, 0), new Range(8, 0));
		assertEquals("", empty.before().text());
		assertEquals("", empty.after().text());
		Chunk context = new Chunk(List.of(change("same\n", Revision.CONTEXT)), new Range(8, 1), new Range(8, 1));
		assertEquals(context.before(), context.after());
		assertDoesNotThrow(() -> Chunk.validateChunks(List.of(empty, context)));
	}

	@Test
	void validatesChunkStartsAndAdvancesBothEnds() {
		Chunk first = new Chunk(List.of(change("a\n", Revision.CONTEXT), change("added\n", Revision.INSERTION)),
				new Range(2, 1), new Range(2, 2));
		Chunk second = new Chunk(List.of(change("b\n", Revision.CONTEXT)), new Range(9, 1), new Range(10, 1));
		assertDoesNotThrow(() -> Chunk.validateChunks(List.of(first, second)));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateChunks(List.of(second, first)));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateChunks(List.of(first, first)));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateChunks(List.of(first,
				new Chunk(second.changes(), new Range(9, 1), new Range(9, 1)))));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateChunks(List.of(
				new Chunk(List.of(), new Range(1, 0), new Range(2, 0)))));
	}

	@Test
	void forbidsTextAndGapsAfterAnUnterminatedFragment() {
		Fragment terminal = new Fragment(Chunk.linesOf("last"), new Range(2, 1));
		Fragment boundary = new Fragment(List.of(), new Range(3, 0));
		assertDoesNotThrow(() -> Chunk.validateFragments(List.of(terminal, boundary)));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateFragments(List.of(terminal, boundary,
				new Fragment(Chunk.linesOf("more\n"), new Range(3, 1)))));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateFragments(List.of(terminal,
				new Fragment(List.of(), new Range(4, 0)))));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateChunks(List.of(
				new Chunk(List.of(change("last", Revision.CONTEXT)), new Range(0, 1), new Range(0, 1)),
				new Chunk(List.of(change("more\n", Revision.CONTEXT)), new Range(1, 1), new Range(1, 1)))));
	}

	@Test
	void copiesCollectionsAndExposesImmutableResults() {
		List<Line> lines = new ArrayList<>(Chunk.linesOf("a\n"));
		Fragment fragment = new Fragment(lines, new Range(0, 1));
		lines.clear();
		assertEquals("a\n", fragment.text());
		assertThrows(UnsupportedOperationException.class, () -> fragment.lines().clear());
		List<Change> changes = new ArrayList<>(List.of(change("a\n", Revision.CONTEXT)));
		Chunk chunk = new Chunk(changes, new Range(0, 1), new Range(0, 1));
		changes.clear();
		assertEquals("a\n", chunk.before().text());
		assertThrows(UnsupportedOperationException.class, () -> chunk.changes().clear());
		assertThrows(UnsupportedOperationException.class, () -> Chunk.linesOf("a").clear());
		assertThrows(UnsupportedOperationException.class, () -> Chunk.diff("a", "b").clear());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 3 })
	void mergesTouchingContextWindowsAndSeparatesDistantEdits(int context) {
		for (int gap : new int[] { 0, 2 * context, 2 * context + 1 }) {
			String middle = IntStream.range(0, gap).mapToObj(index -> "same" + index + "\n").collect(Collectors.joining());
			String before = "oldFirst\n" + middle + "oldLast\n";
			String after = "newFirst\n" + middle + "newLast\n";
			List<Chunk> chunks = Chunk.diff(before, after, context);
			assertEquals(gap <= 2 * context ? 1 : 2, chunks.size());
			assertRoundTrip(before, after, chunks);
		}
	}

	@Test
	void defaultsToThreeContextLinesAndClipsToFileBoundaries() {
		String before = IntStream.range(0, 20).mapToObj(index -> "line" + index + "\n").collect(Collectors.joining());
		String after = before.replace("line10\n", "changed\n");
		Chunk chunk = Chunk.diff(before, after).getFirst();
		assertEquals(new Range(7, 7), chunk.beforeRange());
		assertEquals(new Range(7, 7), chunk.afterRange());
		assertEquals(Chunk.diff(before, after, 3), Chunk.diff(before, after));
		List<Chunk> whole = Chunk.diff(before, after, Integer.MAX_VALUE);
		assertEquals(new Range(0, 20), whole.getFirst().beforeRange());
		assertRoundTrip(before, after, whole);
	}

	@Test
	void tracksShiftedPositionsAcrossMultipleChunks() {
		String before = IntStream.range(0, 12).mapToObj(index -> "line" + index + "\n").collect(Collectors.joining());
		String after = "added1\nadded2\n" + before.replace("line8\n", "changed\n");
		List<Chunk> chunks = Chunk.diff(before, after, 0);
		assertEquals(2, chunks.size());
		assertEquals(new Range(0, 0), chunks.get(0).beforeRange());
		assertEquals(new Range(0, 2), chunks.get(0).afterRange());
		assertEquals(new Range(8, 1), chunks.get(1).beforeRange());
		assertEquals(new Range(10, 1), chunks.get(1).afterRange());
		assertRoundTrip(before, after, chunks);
	}

	@Test
	void handlesCreationDeletionAndChangesAtBothEnds() {
		for (String[] pair : List.of(
				new String[] { "", "a\nb" }, new String[] { "a\nb", "" },
				new String[] { "a\nb\n", "first\na\nb\nlast" },
				new String[] { "a\nb\nc\n", "a\nx\nb\nc\n" },
				new String[] { "a\nb", "a\nb\n" }, new String[] { "\n", "\r\n" },
				new String[] { "a\nb\r\nc\rd", "a\r\nb\nc\rd\n" })) {
			for (int context : new int[] { 0, 1, 3 }) {
				assertRoundTrip(pair[0], pair[1], Chunk.diff(pair[0], pair[1], context));
			}
		}
	}

	@Test
	void producesDeterministicEditsForRepeatedLines() {
		String before = "a\nb\na\nb\na\n";
		String after = "b\na\nb\na\nb\n";
		List<Chunk> chunks = Chunk.diff(before, after, 0);
		assertEquals(chunks, Chunk.diff(before, after, 0));
		assertEquals(2, editCount(chunks));
		assertRoundTrip(before, after, chunks);
	}

	@Test
	void returnsNoChunksForIdenticalInputsAndRejectsNegativeContext() {
		assertEquals(List.of(), Chunk.diff("", ""));
		assertEquals(List.of(), Chunk.diff("same\r\nlast", "same\r\nlast"));
		assertThrows(UnsupportedOperationException.class, () -> Chunk.diff("", "").add(
				new Chunk(List.of(), new Range(0, 0), new Range(0, 0))));
		assertThrows(IllegalArgumentException.class, () -> Chunk.diff("same", "same", -1));
	}

	@Test
	void rejectsNullInputsAndCollectionElements() {
		assertThrows(NullPointerException.class, () -> Chunk.diff(null, ""));
		assertThrows(NullPointerException.class, () -> Chunk.diff("", null));
		assertThrows(NullPointerException.class, () -> Chunk.linesOf(null));
		assertThrows(NullPointerException.class, () -> new Fragment(Arrays.asList((Line) null), new Range(0, 1)));
		assertThrows(NullPointerException.class,
				() -> new Chunk(Arrays.asList((Change) null), new Range(0, 0), new Range(0, 0)));
	}

	@Test
	void exhaustiveSmallInputsProduceShortestReversibleEdits() {
		List<String> texts = new ArrayList<>(List.of(""));
		for (int length = 1; length <= 4; length++) {
			for (int bits = 0; bits < 1 << length; bits++) {
				StringBuilder text = new StringBuilder();
				for (int index = 0; index < length; index++) {
					text.append((bits & 1 << index) == 0 ? "a\n" : "b\n");
				}
				texts.add(text.toString());
			}
		}
		for (String before : texts) {
			for (String after : texts) {
				for (int context : new int[] { 0, 1, 3 }) {
					List<Chunk> chunks = Chunk.diff(before, after, context);
					assertEquals(shortestDistance(Chunk.linesOf(before), Chunk.linesOf(after)), editCount(chunks));
					assertRoundTrip(before, after, chunks);
				}
			}
		}
	}

	@Test
	void generatedMixedEndingsProduceShortestReversibleEdits() {
		Random random = new Random(42);
		for (int sample = 0; sample < 300; sample++) {
			String before = randomText(random);
			String after = randomText(random);
			List<Chunk> chunks = Chunk.diff(before, after, random.nextInt(5));
			assertEquals(shortestDistance(Chunk.linesOf(before), Chunk.linesOf(after)), editCount(chunks));
			assertRoundTrip(before, after, chunks);
		}
	}

	private static Change change(String text, Revision revision) {
		return new Change(Chunk.linesOf(text).getFirst(), revision);
	}

	private static long editCount(List<Chunk> chunks) {
		return chunks.stream().flatMap(chunk -> chunk.changes().stream())
				.filter(change -> change.revision() != Revision.CONTEXT).count();
	}

	private static void assertRoundTrip(String before, String after, List<Chunk> chunks) {
		Chunk.validateChunks(chunks);
		chunks.forEach(chunk -> {
			assertFalse(chunk.changes().isEmpty());
			assertTrue(chunk.changes().stream().anyMatch(change -> change.revision() != Revision.CONTEXT));
			assertEquals(chunk, chunk.invert().invert());
			assertEquals(chunk.before(), chunk.invert().after());
			assertEquals(chunk.after(), chunk.invert().before());
		});
		assertEquals(after, apply(before, chunks));
		List<Chunk> reverse = chunks.stream().map(Chunk::invert).toList();
		Chunk.validateChunks(reverse);
		assertEquals(before, apply(after, reverse));
	}

	/**
	 * Applies ranges to a retained baseline without reproducing the diff search.
	 */
	private static String apply(String text, List<Chunk> chunks) {
		List<Line> source = Chunk.linesOf(text);
		List<Line> result = new ArrayList<>();
		int end = 0;
		for (Chunk chunk : chunks) {
			assertEquals(source.subList(chunk.beforeRange().start(), chunk.beforeRange().end()), chunk.before().lines());
			result.addAll(source.subList(end, chunk.beforeRange().start()));
			assertEquals(chunk.afterRange().start(), result.size());
			result.addAll(chunk.after().lines());
			end = chunk.beforeRange().end();
		}
		result.addAll(source.subList(end, source.size()));
		return result.stream().map(line -> line.text() + line.ending().getValue()).collect(Collectors.joining());
	}

	/** Independent dynamic-programming oracle for insertion/deletion distance. */
	private static int shortestDistance(List<Line> before, List<Line> after) {
		int[][] distance = new int[before.size() + 1][after.size() + 1];
		for (int x = 0; x <= before.size(); x++) {
			for (int y = 0; y <= after.size(); y++) {
				distance[x][y] = x == 0 ? y
						: y == 0 ? x
								: before.get(x - 1).equals(after.get(y - 1)) ? distance[x - 1][y - 1]
										: 1 + Math.min(distance[x - 1][y], distance[x][y - 1]);
			}
		}
		return distance[before.size()][after.size()];
	}

	private static String randomText(Random random) {
		StringBuilder result = new StringBuilder();
		int length = random.nextInt(13);
		for (int index = 0; index < length; index++) {
			result.append(switch (random.nextInt(3)) {
				case 0 -> "a";
				case 1 -> "b";
				default -> "";
			});
			result.append(index == length - 1 && random.nextBoolean() ? "" : switch (random.nextInt(3)) {
				case 0 -> "\n";
				case 1 -> "\r\n";
				default -> "\r";
			});
		}
		return result.toString();
	}
}
