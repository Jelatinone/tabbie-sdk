package cat.tabbie.sdk.album.revision;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import cat.tabbie.sdk.album.revision.Chunk.Change;
import cat.tabbie.sdk.album.revision.Chunk.Change.Line;
import cat.tabbie.sdk.album.revision.Chunk.Change.Revision;
import cat.tabbie.sdk.album.revision.Chunk.Change.Sequence;
import cat.tabbie.sdk.album.revision.Chunk.Fragment;
import cat.tabbie.sdk.album.revision.Chunk.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkTest {

	private static final int GENERATED_CASES = 400;

	@Test
	void linesOf_splitsExactEndings_withoutInventingATrailingLine() {
		String text = "a\nb\r\nc\rd";

		List<Line> lines = Chunk.linesOf(text);

		assertEquals(List.of(
				new Line("a", Sequence.LF),
				new Line("b", Sequence.CRLF),
				new Line("c", Sequence.CR),
				new Line("d", Sequence.NONE)), lines);
		assertEquals(List.of(), Chunk.linesOf(""));
		assertEquals(List.of(new Line("x", Sequence.LF)), Chunk.linesOf("x\n"));
		assertEquals(List.of(new Line("", Sequence.CRLF), new Line("", Sequence.LF)), Chunk.linesOf("\r\n\n"));
	}

	@Test
	void fragmentsOf_describesCompleteText_andNothingForEmptyText() {
		List<Fragment> fragments = Chunk.fragmentsOf("a\nb");

		assertEquals(1, fragments.size());
		assertEquals(new Range(0, 2), fragments.getFirst().range());
		assertEquals("a\nb", fragments.getFirst().text());
		assertEquals(List.of(), Chunk.fragmentsOf(""));
	}

	@Test
	void diff_returnsNothing_forIdenticalText_andRejectsNegativeContext() {
		assertEquals(List.of(), Chunk.diff("a\nb\n", "a\nb\n"));
		assertThrows(IllegalArgumentException.class, () -> Chunk.diff("a", "b", -1));
	}

	@Test
	void diff_describesOneReplacement_withSurroundingContext() {
		String before = "1\n2\n3\n4\n5\n6\n7\n";
		String after = "1\n2\n3\nfour\n5\n6\n7\n";

		List<Chunk> chunks = Chunk.diff(before, after);

		assertEquals(1, chunks.size());
		Chunk chunk = chunks.getFirst();
		assertEquals(new Range(0, 7), chunk.beforeRange());
		assertEquals(new Range(0, 7), chunk.afterRange());
		assertEquals(List.of(Revision.CONTEXT, Revision.CONTEXT, Revision.CONTEXT, Revision.DELETION,
				Revision.INSERTION, Revision.CONTEXT, Revision.CONTEXT, Revision.CONTEXT),
				chunk.changes().stream().map(Change::revision).toList());
		assertEquals("1\n2\n3\n4\n5\n6\n7\n", chunk.before().text());
		assertEquals("1\n2\n3\nfour\n5\n6\n7\n", chunk.after().text());
	}

	@Test
	void diff_mergesEditsSeparatedBySixLines_andSplitsEditsSeparatedBySeven() {
		String six = "x\n1\n2\n3\n4\n5\n6\nx\n";
		String seven = "x\n1\n2\n3\n4\n5\n6\n7\nx\n";

		int merged = Chunk.diff(six, six.replace("x", "y")).size();
		int split = Chunk.diff(seven, seven.replace("x", "y")).size();

		assertEquals(1, merged);
		assertEquals(2, split);
	}

	@Test
	void diff_withZeroContext_mergesOnlyAdjacentEdits() {
		String before = "a\nb\nc\nd\n";
		String after = "A\nB\nc\nD\n";

		List<Chunk> chunks = Chunk.diff(before, after, 0);

		assertEquals(2, chunks.size());
		assertTrue(chunks.stream()
				.flatMap(chunk -> chunk.changes().stream())
				.noneMatch(change -> change.revision() == Revision.CONTEXT));
		assertEquals(new Range(0, 2), chunks.get(0).beforeRange());
		assertEquals(new Range(3, 1), chunks.get(1).beforeRange());
	}

	@Test
	void diff_treatsLineEndingsAsPartOfEquality() {
		List<Chunk> chunks = Chunk.diff("a\n", "a\r\n");

		assertEquals(1, chunks.size());
		assertEquals(List.of(Revision.DELETION, Revision.INSERTION),
				chunks.getFirst().changes().stream().map(Change::revision).toList());
	}

	@Test
	void diff_reconstructsTheResult_withAShortestScript_forGeneratedText() {
		Random random = new Random(20261005L);

		for (int index = 0; index < GENERATED_CASES; index++) {
			String before = text(random);
			String after = text(random);
			int contextLines = random.nextInt(4);

			List<Chunk> chunks = Chunk.diff(before, after, contextLines);

			assertEquals(after, render(apply(chunks, Chunk.linesOf(before))),
					() -> "diff of " + quote(before) + " to " + quote(after));
			assertEquals(minimumEdits(Chunk.linesOf(before), Chunk.linesOf(after)), edits(chunks),
					() -> "shortest script for " + quote(before) + " to " + quote(after));
			assertTrue(chunks.stream().allMatch(ChunkTest::hasEdit), "every generated chunk edits");
		}
	}

	@Test
	void invert_swapsRolesAndRanges_andUndoesTheChange() {
		Random random = new Random(7L);

		for (int index = 0; index < GENERATED_CASES; index++) {
			String before = text(random);
			String after = text(random);

			List<Chunk> inverted = Chunk.diff(before, after).stream().map(Chunk::invert).toList();

			assertEquals(before, render(apply(inverted, Chunk.linesOf(after))));
		}
	}

	@Test
	void between_keepsUnchangedLinesAsContext() {
		Fragment before = new Fragment(Chunk.linesOf("a\nb\nc\n"), new Range(4, 3));
		Fragment after = new Fragment(Chunk.linesOf("a\nB\nc\n"), new Range(4, 3));

		Chunk chunk = Chunk.between(before, after);

		assertEquals(new Range(4, 3), chunk.beforeRange());
		assertEquals(List.of(Revision.CONTEXT, Revision.DELETION, Revision.INSERTION, Revision.CONTEXT),
				chunk.changes().stream().map(Change::revision).toList());
	}

	@Test
	void between_pairsFragmentsByIndex_andRejectsMismatchedGaps() {
		List<Fragment> before = List.of(
				new Fragment(Chunk.linesOf("a\n"), new Range(0, 1)),
				new Fragment(Chunk.linesOf("c\n"), new Range(2, 1)));
		List<Fragment> after = List.of(
				new Fragment(Chunk.linesOf("A\n"), new Range(0, 1)),
				new Fragment(Chunk.linesOf("C\n"), new Range(2, 1)));
		List<Fragment> shifted = List.of(
				new Fragment(Chunk.linesOf("A\n"), new Range(0, 1)),
				new Fragment(Chunk.linesOf("C\n"), new Range(3, 1)));

		List<Chunk> chunks = Chunk.between(before, after);

		assertEquals(2, chunks.size());
		assertThrows(IllegalArgumentException.class, () -> Chunk.between(before, shifted));
		assertThrows(IllegalArgumentException.class, () -> Chunk.between(before, after.subList(0, 1)));
	}

	@Test
	void create_insertsCompleteText_andRejectsGaps() {
		List<Fragment> complete = List.of(
				new Fragment(Chunk.linesOf("a\n"), new Range(0, 1)),
				new Fragment(Chunk.linesOf("b\n"), new Range(1, 1)));
		List<Fragment> gapped = List.of(new Fragment(Chunk.linesOf("b\n"), new Range(1, 1)));

		List<Chunk> chunks = Chunk.create(complete);

		assertEquals("a\nb\n", render(apply(chunks, List.of())));
		assertTrue(chunks.stream().allMatch(chunk -> chunk.beforeRange().equals(new Range(0, 0))));
		assertThrows(IllegalArgumentException.class, () -> Chunk.create(gapped));
	}

	@Test
	void validateFragments_rejectsOverlap_andTextAfterAnUnterminatedLine() {
		Fragment first = new Fragment(Chunk.linesOf("a\nb\n"), new Range(0, 2));
		Fragment overlapping = new Fragment(Chunk.linesOf("c\n"), new Range(1, 1));
		Fragment unterminated = new Fragment(Chunk.linesOf("end"), new Range(0, 1));
		Fragment boundary = new Fragment(List.of(), new Range(1, 0));
		Fragment later = new Fragment(Chunk.linesOf("more\n"), new Range(1, 1));

		Chunk.validateFragments(List.of(unterminated, boundary));

		assertThrows(IllegalArgumentException.class, () -> Chunk.validateFragments(List.of(first, overlapping)));
		assertThrows(IllegalArgumentException.class, () -> Chunk.validateFragments(List.of(unterminated, later)));
	}

	@Test
	void records_rejectInconsistentShapes() {
		List<Change> oneContext = List.of(new Change(new Line("a", Sequence.LF), Revision.CONTEXT));

		assertThrows(IllegalArgumentException.class, () -> new Chunk(oneContext, new Range(0, 2), new Range(0, 1)));
		assertThrows(IllegalArgumentException.class, () -> new Line("a\nb", Sequence.LF));
		assertThrows(IllegalArgumentException.class, () -> new Range(-1, 0));
		assertThrows(IllegalArgumentException.class, () -> new Range(Integer.MAX_VALUE, 1));
		assertThrows(IllegalArgumentException.class,
				() -> new Fragment(List.of(new Line("a", Sequence.NONE), new Line("b", Sequence.LF)), new Range(0, 2)));
		assertThrows(NullPointerException.class, () -> new Chunk(null, new Range(0, 0), new Range(0, 0)));
	}

	@Test
	void revision_invertsInsertionAndDeletion_andKeepsContext() {
		assertEquals(Revision.DELETION, Revision.INSERTION.invert());
		assertEquals(Revision.INSERTION, Revision.DELETION.invert());
		assertEquals(Revision.CONTEXT, Revision.CONTEXT.invert());
	}

	@Test
	void compose_keepsSeparateRegions_withRangesShiftedByEarlierEdits() {
		String original = "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\n";
		String intermediate = "a\nnew\nb\nc\nd\ne\nf\ng\nh\ni\nj\n";
		String result = "a\nnew\nb\nc\nd\ne\nf\ng\nh\ni\nJ\n";

		List<Chunk> composed = Chunk.compose(Chunk.diff(original, intermediate, 0), Chunk.diff(intermediate, result, 0));

		assertEquals(2, composed.size());
		assertEquals(new Range(9, 1), composed.get(1).beforeRange());
		assertEquals(new Range(10, 1), composed.get(1).afterRange());
		assertEquals(result, render(apply(composed, Chunk.linesOf(original))));
	}

	@Test
	void compose_cancelsAChangeFollowedByItsInverse() {
		List<Chunk> forward = Chunk.diff("a\nb\nc\n", "a\nB\nc\n");
		List<Chunk> backward = forward.stream().map(Chunk::invert).toList();

		List<Chunk> composed = Chunk.compose(forward, backward);

		assertEquals(List.of(), composed);
	}

	@Test
	void compose_rejectsSequencesThatDisagreeAboutTheIntermediateText() {
		List<Chunk> first = Chunk.diff("a\nb\n", "a\nB\n");
		List<Chunk> second = Chunk.diff("a\nX\n", "a\nY\n");

		assertThrows(IllegalArgumentException.class, () -> Chunk.compose(first, second));
	}

	@Test
	void compose_reconstructsTheEndToEndChange_forGeneratedSequences() {
		Random random = new Random(42L);

		for (int index = 0; index < GENERATED_CASES; index++) {
			String original = text(random);
			String intermediate = text(random);
			String result = text(random);
			List<Chunk> first = Chunk.diff(original, intermediate, random.nextInt(3));
			List<Chunk> second = Chunk.diff(intermediate, result, random.nextInt(3));

			List<Chunk> composed = Chunk.compose(first, second);

			assertEquals(result, render(apply(composed, Chunk.linesOf(original))),
					() -> "composition of " + quote(original) + ", " + quote(intermediate) + ", " + quote(result));
		}
	}

	/**
	 * Applies chunks to complete original lines, checking every described line
	 * and range against the text actually being changed.
	 */
	static List<Line> apply(List<Chunk> chunks, List<Line> original) {
		List<Line> result = new ArrayList<>();
		int cursor = 0;
		for (Chunk chunk : chunks) {
			int start = chunk.beforeRange().start();
			assertTrue(start >= cursor, "chunks must be ordered and non-overlapping");
			result.addAll(original.subList(cursor, start));
			cursor = start;
			assertEquals(chunk.afterRange().start(), result.size(), "after range must start where the chunk applies");
			for (Change change : chunk.changes()) {
				switch (change.revision()) {
					case CONTEXT -> {
						assertEquals(original.get(cursor++), change.line(), "context must match the original");
						result.add(change.line());
					}
					case DELETION -> assertEquals(original.get(cursor++), change.line(), "deletion must match");
					case INSERTION -> result.add(change.line());
				}
			}
		}
		result.addAll(original.subList(cursor, original.size()));
		return result;
	}

	static String render(List<Line> lines) {
		return lines.stream()
				.map(line -> line.text() + line.ending().getValue())
				.collect(Collectors.joining());
	}

	/**
	 * Generates short text over a small alphabet, so lines repeat often, with an
	 * occasional unterminated final line or mixed ending.
	 */
	static String text(Random random) {
		int count = random.nextInt(9);
		StringBuilder text = new StringBuilder();
		for (int index = 0; index < count; index++) {
			text.append((char) ('a' + random.nextInt(4)));
			boolean last = index == count - 1;
			int ending = random.nextInt(10);
			if (last && ending == 0) {
				break;
			}
			text.append(ending == 1 ? "\r\n" : "\n");
		}
		return text.toString();
	}

	private static boolean hasEdit(Chunk chunk) {
		return chunk.changes().stream().anyMatch(change -> change.revision() != Revision.CONTEXT);
	}

	private static int edits(List<Chunk> chunks) {
		return (int) chunks.stream()
				.flatMap(chunk -> chunk.changes().stream())
				.filter(change -> change.revision() != Revision.CONTEXT)
				.count();
	}

	private static int minimumEdits(List<Line> before, List<Line> after) {
		int[][] common = new int[before.size() + 1][after.size() + 1];
		for (int row = before.size() - 1; row >= 0; row--) {
			for (int column = after.size() - 1; column >= 0; column--) {
				common[row][column] = before.get(row).equals(after.get(column))
						? common[row + 1][column + 1] + 1
						: Math.max(common[row + 1][column], common[row][column + 1]);
			}
		}
		return before.size() + after.size() - 2 * common[0][0];
	}

	private static String quote(String text) {
		return "\"" + text.replace("\r", "\\r").replace("\n", "\\n") + "\"";
	}
}
