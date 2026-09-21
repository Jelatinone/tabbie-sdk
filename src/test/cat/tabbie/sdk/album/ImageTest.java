package cat.tabbie.sdk.album;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cat.tabbie.sdk.album.Chunk.Fragment;
import cat.tabbie.sdk.album.Chunk.Range;
import cat.tabbie.sdk.album.Image.File;
import cat.tabbie.sdk.album.Image.Text;

class ImageTest {

  private static final Path PATH = Path.of("config", "..", "settings.txt");
  private static final Reference EMPTY = Reference.of("empty.bin", new byte[0]);

  @Test
  void createsAndDeletesWholeFilesWithoutChangingReferencesOrPaths() {
    Image<File> creation = Image.create(PATH, EMPTY);
    assertInstanceOf(Installation.class, creation);
    assertInstanceOf(File.Absent.class, creation.before());
    assertSame(EMPTY, assertInstanceOf(File.Present.class, creation.after()).reference());
    assertSame(PATH, creation.path());
    assertEquals(Image.delete(PATH, EMPTY), creation.preimage());
    assertEquals(creation, creation.preimage().preimage());
  }

  @Test
  void installationsAllowReplacementAndRejectBothAbsent() {
    assertThrows(IllegalArgumentException.class, () -> new Installation(new File.Absent(), new File.Absent(), PATH));
    assertEquals(Image.replace(PATH, EMPTY, EMPTY), Image.replace(PATH, EMPTY, EMPTY).preimage());
  }

  @Test
  void replacementsInvertExactBeforeAndAfterContent() {
    Reference old = Reference.of("old.bin", new byte[]{1, 2});
    Reference next = Reference.of("next.bin", new byte[]{3, 4});
    Image<File> replacement = Image.replace(PATH, old, next);
    assertEquals(old, assertInstanceOf(File.Present.class, replacement.before()).reference());
    assertEquals(next, assertInstanceOf(File.Present.class, replacement.after()).reference());
    assertEquals(Image.replace(PATH, next, old), replacement.preimage());
    assertEquals(replacement, replacement.preimage().preimage());
  }

  @Test
  void emptyContentStillAssertsFileExistence() {
    Image<File> binary = Image.create(PATH, EMPTY);
    assertInstanceOf(File.Absent.class, binary.before());
    assertEquals(0, assertInstanceOf(File.Present.class, binary.after()).reference().size());
    Image<Text> text = Image.configure(PATH, "");
    assertInstanceOf(Text.Absent.class, text.before());
    assertEquals(List.of(), present(text.after()).fragments());
    assertEquals(Image.delete(PATH, ""), text.preimage());
  }

  @Test
  void projectsChunksAndPreservesEmptyInsertionFragments() {
    List<Chunk> chunks = Chunk.diff("a\nb\nc\n", "a\ninserted\nb\nc\n", 0);
    Image<Text> image = Image.configure(PATH, chunks);
    assertInstanceOf(Configuration.class, image);
    assertSame(PATH, image.path());
    assertEquals(chunks.stream().map(Chunk::before).toList(), present(image.before()).fragments());
    assertEquals(chunks.stream().map(Chunk::after).toList(), present(image.after()).fragments());
    assertEquals(new Range(1, 0), present(image.before()).fragments().getFirst().range());
    assertEquals(new Range(1, 1), present(image.after()).fragments().getFirst().range());
    assertEquals(new Configuration(chunks, PATH), image);
    assertEquals(image, image.preimage().preimage());
    assertEquals(image.before(), image.preimage().after());
  }

  @Test
  void stringFactoriesRetainSelectedRangesWithDefaultOrExplicitContext() {
    String before = "0\n1\n2\n3\n4\n5\n6\n7\n8\n";
    String after = before.replace("4\n", "changed\n");
    assertEquals(Image.configure(PATH, Chunk.diff(before, after)), Image.configure(PATH, before, after));
    Image<Text> image = Image.configure(PATH, before, after, 0);
    assertEquals(List.of(fragment("4\n", 4)), present(image.before()).fragments());
    assertEquals(List.of(fragment("changed\n", 4)), present(image.after()).fragments());
    assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH, before, after, -1));
  }

  @Test
  void unchangedTextOnlyAssertsExistence() {
    Image<Text> image = Image.configure(PATH, "existing content", "existing content");
    assertEquals(List.of(), present(image.before()).fragments());
    assertEquals(List.of(), present(image.after()).fragments());
    assertEquals(image, Image.configure(PATH, "", ""));
    assertEquals(image, Image.configure(PATH, List.of()));
    assertEquals(image, image.preimage());
  }

  @Test
  void requiresPairedFragmentsWithMatchingGaps() {
    Text.Present before = new Text.Present(List.of(fragment("a\n", 2), fragment("b\n", 8)));
    Text.Present after = new Text.Present(List.of(fragment("a\nadded\n", 2), fragment("changed\n", 9)));
    assertDoesNotThrow(() -> Image.configure(PATH, before, after));
    assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH, before,
        new Text.Present(List.of(fragment("a\nadded\n", 2), fragment("changed\n", 8)))));
    assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH, before,
        new Text.Present(List.of(fragment("a\n", 2)))));
    assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH,
        new Text.Present(List.of(fragment("a\n", 1))), new Text.Present(List.of(fragment("b\n", 2)))));
  }

  @Test
  void requiresCompletePresentSideForCreationAndDeletion() {
    Text.Absent absent = new Text.Absent();
    assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH, absent, absent));
    for (Text.Present incomplete : List.of(new Text.Present(List.of(fragment("a\n", 1))),
        new Text.Present(List.of(fragment("a\n", 0), fragment("b\n", 2))))) {
      assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH, absent, incomplete));
      assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH, incomplete, absent));
    }
    Text.Present complete = new Text.Present(List.of(fragment("a\n", 0), fragment("last", 1)));
    assertEquals(Image.configure(PATH, absent, complete).preimage(), Image.configure(PATH, complete, absent));
    assertDoesNotThrow(() -> Image.configure(PATH, absent, new Text.Present(List.of(fragment("", 0)))));
  }

  @Test
  void validatesTextStatesDuringConstruction() {
    assertThrows(IllegalArgumentException.class,
        () -> new Text.Present(List.of(fragment("a\nb\n", 0), fragment("c\n", 1))));
    assertThrows(IllegalArgumentException.class,
        () -> new Text.Present(List.of(fragment("a\n", 2), fragment("b\n", 1))));
    assertThrows(IllegalArgumentException.class,
        () -> new Text.Present(List.of(fragment("last", 0), fragment("extra\n", 1))));
    List<Chunk> invalid = List.of(new Chunk(List.of(), new Range(0, 0), new Range(1, 0)));
    assertThrows(IllegalArgumentException.class, () -> Image.configure(PATH, invalid));
    assertThrows(IllegalArgumentException.class, () -> new Configuration(invalid, PATH));
  }

  @Test
  void defensivelyCopiesFragmentAndChunkInputs() {
    List<Fragment> fragments = new ArrayList<>(List.of(fragment("a\n", 0)));
    Text.Present state = new Text.Present(fragments);
    fragments.clear();
    assertEquals(List.of(fragment("a\n", 0)), state.fragments());
    assertThrows(UnsupportedOperationException.class, () -> state.fragments().clear());
    List<Chunk> chunks = new ArrayList<>(Chunk.diff("old", "new"));
    Image<Text> image = Image.configure(PATH, chunks);
    chunks.clear();
    assertEquals("new", present(image.after()).fragments().getFirst().text());
  }

  @Test
  void rejectsNullStatesPathsAndListElements() {
    assertThrows(NullPointerException.class, () -> Image.create(null, EMPTY));
    assertThrows(NullPointerException.class, () -> Image.configure(PATH, (List<Chunk>) null));
    assertThrows(NullPointerException.class, () -> new Configuration((List<Chunk>) null, PATH));
    assertThrows(NullPointerException.class, () -> Image.configure(PATH, (Text) null, new Text.Absent()));
    assertThrows(NullPointerException.class, () -> new Text.Present(Arrays.asList((Fragment) null)));
    assertThrows(NullPointerException.class, () -> Image.configure(PATH, Arrays.asList((Chunk) null)));
  }

  private static Text.Present present(Text text) {
    return assertInstanceOf(Text.Present.class, text);
  }

  private static Fragment fragment(String text, int start) {
    List<Chunk.Change.Line> lines = Chunk.linesOf(text);
    return new Fragment(lines, new Range(start, lines.size()));
  }
}
