package cat.tabbie.sdk.album;

import java.nio.file.Path;
import java.util.List;

import lombok.NonNull;

/**
 *
 * <h1>Image</h1>
 *
 * @param <State> Description of the underlying filesystem state
 *
 *                <p>
 *                An immutable description of a change to a file's content or
 *                existence. Images perform no I/O and track no filesystem
 *                metadata. A reverse image describes the inverse change;
 *                checking and applying either image belongs to its consumer.
 *                </p>
 */
public sealed interface Image<State extends Image.Alteration> {

	/**
	 * Target path location, retained as supplied by the caller
	 *
	 * @return target path
	 */
	Path path();

	/**
	 * Expected content or existence before the change
	 *
	 * @return expected content before
	 */
	State before();

	/**
	 * Described content or existence after the change
	 *
	 * @return described content after
	 */
	State after();

	/**
	 * Image with its before and after states exchanged
	 *
	 * @return image reversed
	 */
	Image<State> preimage();

	/**
	 * Describes creation of a file from retained content, including zero bytes.
	 *
	 * @param path  target path
	 * @param after file reference
	 *
	 * @return Resulting image
	 */
	static Image<File> create(@NonNull Path path, @NonNull Reference.Captured after) {
		return new Installation(new File.Absent(), new File.Present(after), path);
	}

	/**
	 * Describes replacement of a file deleted and succeeded by another file
	 * created.
	 * 
	 * @param path   target path
	 * @param before file reference before
	 * @param after  file reference after
	 * 
	 * @return resulting image
	 */
	static Image<File> replace(Path path, Reference.Captured before, Reference.Captured after) {
		return new Installation(new File.Present(before), new File.Present(after), path);
	}

	/**
	 * Describes deletion of a file whose complete content matches the reference.
	 *
	 * @param path   target path
	 * @param before file reference
	 *
	 * @return resulting image
	 */
	static Image<File> delete(@NonNull Path path, @NonNull Reference.Captured before) {
		return new Installation(new File.Present(before), new File.Absent(), path);
	}

	/**
	 * Describes deletion of the complete supplied text, including an empty file.
	 *
	 * @param path   target path
	 * @param before file reference before
	 *
	 * @return resulting image
	 */
	static Image<Text> delete(@NonNull Path path, @NonNull String before) {
		return configure(path, new Text.Present(
				Chunk.diff(before, "").stream().map(Chunk::before).toList()), new Text.Absent());
	}

	/**
	 * Describes a text change using explicit existence and fragment states.
	 *
	 * @param path   target path
	 * @param before text before
	 * @param after  text after
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Path path, @NonNull Text before, @NonNull Text after) {
		return new Configuration(before, after, path);
	}

	/**
	 * Describes changes to an existing file. Each chunk contributes a fragment on
	 * each side; the chunks' insertion/deletion ordering is not retained.
	 *
	 * @param path   target path
	 * @param chunks changed chunks
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Path path, @NonNull List<Chunk> chunks) {
		return new Configuration(chunks, path);
	}

	/**
	 * Describes text differences with three surrounding context lines.
	 *
	 * @param path   target path
	 * @param before text before
	 * @param after  text after
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Path path, @NonNull String before, @NonNull String after) {
		return configure(path, Chunk.diff(before, after));
	}

	/**
	 * Retains only the differing regions and their context, not complete content
	 * expectations. Equal inputs produce empty fragment lists: the file must
	 * exist, but no particular content is described.
	 *
	 * @param path         target path
	 * @param before       text before
	 * @param after        text after
	 * @param contextLines number of context lines to keep
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Path path, @NonNull String before, @NonNull String after,
			int contextLines) {
		return configure(path, Chunk.diff(before, after, contextLines));
	}

	/**
	 * Describes creation of the complete supplied text; empty text is present.
	 *
	 * @param path  target path
	 * @param after text after
	 * 
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Path path, @NonNull String after) {
		return configure(path, new Text.Absent(), new Text.Present(
				Chunk.diff("", after).stream().map(Chunk::after).toList()));
	}

	/**
	 *
	 * <h1>Alteration</h1>
	 *
	 * <p>
	 * Describes the underlying filesystem state of a file's contents or existence.
	 * </p>
	 *
	 */
	sealed interface Alteration {
	}

	sealed interface File extends Alteration {

		record Absent() implements File {
		}

		record Present(@NonNull Reference.Captured reference) implements File {
		}
	}

	sealed interface Text extends Alteration {

		record Absent() implements Text {
		}

		record Present(@NonNull List<Chunk.Fragment> fragments) implements Text {

			public Present {
				fragments = List.copyOf(fragments);
				Chunk.validateFragments(fragments);
			}
		}
	}
}

/**
 *
 * <h1>Installation</h1>
 *
 * <p>
 * Creates or deletes a whole file. Exactly one side must be absent.
 * </p>
 */
record Installation(@NonNull File before, @NonNull File after, @NonNull Path path) implements Image<Image.File> {
	public Installation {
		if (before instanceof File.Absent && after instanceof File.Absent) {
			throw new IllegalArgumentException("Files may not both be absent!");
		}
	}

	@Override
	public Installation preimage() {
		return new Installation(after, before, path);
	}
}

/**
 *
 * <h1>Configuration</h1>
 *
 * <p>
 * Stores corresponding text fragments, with equal unchanged gaps on both sides.
 * Creation/deletion describes all content from line zero without gaps;
 * consumers
 * must also check actual EOF when matching complete content. Both absent is
 * invalid; both present with no fragments is an unchanged existence assertion.
 * </p>
 */
record Configuration(@NonNull Text before, @NonNull Text after, @NonNull Path path) implements Image<Image.Text> {

	public Configuration(@NonNull List<Chunk> chunks, @NonNull Path path) {
		this(new Text.Present(chunks.stream().map(Chunk::before).toList()),
				new Text.Present(chunks.stream().map(Chunk::after).toList()), path);
	}

	public Configuration {
		validate(before, after);
	}

	@Override
	public Image<Text> preimage() {
		return new Configuration(after, before, path);
	}

	/**
	 * Validate text before and after
	 *
	 * @param before text before
	 * @param after  text after
	 *
	 * @throws IllegalArgumentException when text states are both absent or gaps
	 *                                  have occurred
	 */
	private static void validate(Text before, Text after) {
		if (before instanceof Text.Absent && after instanceof Text.Absent) {
			throw new IllegalArgumentException("Text states may not both be absent.");
		}
		if (before instanceof Text.Present(List<Chunk.Fragment> beforeText)
				&& after instanceof Text.Present(List<Chunk.Fragment> afterText)) {
			Chunk.validateFragments(beforeText, afterText);
			return;
		}
		Text.Present present = before instanceof Text.Present text ? text : (Text.Present) after;
		int end = 0;
		for (Chunk.Fragment fragment : present.fragments()) {
			if (fragment.range().start() != end) {
				throw new IllegalArgumentException("Creation/deletion must describe complete text without gaps.");
			}
			end = fragment.range().end();
		}
	}
}
