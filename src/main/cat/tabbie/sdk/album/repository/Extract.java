package cat.tabbie.sdk.album.repository;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * The retention role: captures described sources under their own identity and
 * reopens retained bytes by reference. Each successful capture publishes
 * complete bytes verified by digest and size; a failure publishes no
 * incomplete content. Stores may deduplicate identical bytes across filenames.
 * Retained bytes stay readable until the backend's documented lifetime ends or
 * their owner explicitly reclaims them; core owns revision and backup
 * retention. Implementations document concurrency and limits.
 */
public interface Extract {

	/**
	 * Describes capture of a source, verifying and retaining its bytes under its
	 * own identity. The source is opened only when the returned intermediate is
	 * collapsed, and any expected evidence it declares is verified.
	 * Implementations may use {@link #transfer} to digest while writing.
	 *
	 * @param source   reusable description of the bytes to capture
	 * @param observer transfer callbacks; callbacks must not throw
	 * @return verified reference to retained bytes
	 */
	@NonNull
	Intermediate<Reference.Captured> capture(@NonNull Describe source,
			@NonNull Observer<? super Reference.Captured, ? super Transfer> observer);

	/**
	 * Describes retained bytes by reference. Describing performs no I/O; opening
	 * the result yields exactly the retained bytes, and fails when they are not
	 * retained here or were reclaimed.
	 *
	 * @param reference captured reference previously produced by this backend or
	 *                  one sharing its storage
	 * @return reusable description of the retained bytes
	 */
	@NonNull
	Describe retained(@NonNull Reference.Captured reference);

	/**
	 * Checks whether the referenced bytes are retained, without acquiring them.
	 * Backends that cannot tell cheaply may return false, which only causes a
	 * redundant capture.
	 *
	 * @param reference captured reference to check
	 * @return whether the bytes are retained
	 * @throws IOException when availability cannot be checked
	 */
	default boolean exists(@NonNull Reference.Captured reference) throws IOException {
		return false;
	}

	/**
	 * Describes capture of a source without progress callbacks.
	 *
	 * @param source reusable description of the bytes to capture
	 * @return verified reference to retained bytes
	 */
	@NonNull
	default Intermediate<Reference.Captured> capture(@NonNull Describe source) {
		return capture(source, Observer.none());
	}

	/**
	 * Describes capture of an already-open, caller-owned stream. The stream is
	 * consumed to EOF and left open. The description is single-use: collapsing
	 * it a second time fails rather than reading an exhausted stream.
	 *
	 * @param expected identity and evidence for the stream's bytes
	 * @param source   caller-owned stream
	 * @param observer transfer callbacks
	 * @return verified reference to retained bytes
	 */
	@NonNull
	default Intermediate<Reference.Captured> capture(@NonNull Reference.Pending expected,
			@NonNull InputStream source, @NonNull Observer<? super Reference.Captured, ? super Transfer> observer) {
		return capture(new StreamSource(expected, source, new AtomicBoolean()), observer);
	}

	/**
	 * Describes capture of a defensive copy of caller-owned bytes.
	 *
	 * @param expected identity and evidence for the bytes
	 * @param bytes    complete content
	 * @return verified reference to retained bytes
	 */
	@NonNull
	default Intermediate<Reference.Captured> capture(@NonNull Reference.Pending expected, @NonNull byte[] bytes) {
		return capture(new BytesSource(expected, bytes.clone()));
	}

	/**
	 * Copies a stream to a sink to EOF, computing its SHA-256 digest and size and
	 * verifying any expected evidence. Neither stream is closed. On failure the
	 * observer is notified and the sink may hold partial bytes, which the caller
	 * must not publish.
	 *
	 * @param expected identity and evidence to verify against
	 * @param source   stream to consume
	 * @param sink     destination for the copied bytes
	 * @param observer transfer callbacks
	 * @return verified reference named after the expected filename
	 * @throws IOException when reading or writing fails or evidence differs
	 */
	static Reference.Captured transfer(@NonNull Reference.Pending expected, @NonNull InputStream source,
			@NonNull OutputStream sink, @NonNull Observer<? super Reference.Captured, ? super Transfer> observer)
			throws IOException {
		try {
			MessageDigest digest = sha256();
			OptionalLong total = expected.expectedSize() != null
					? OptionalLong.of(expected.expectedSize())
					: OptionalLong.empty();
			long size = 0;
			byte[] buffer = new byte[8192];
			for (int read; (read = source.read(buffer)) != -1;) {
				digest.update(buffer, 0, read);
				sink.write(buffer, 0, read);
				size = Math.addExact(size, read);
				observer.transfer(new Transfer(size, total));
			}
			String hash = HexFormat.of().formatHex(digest.digest());
			if (expected.expectedSize() != null && expected.expectedSize() != size) {
				throw new IOException("Captured size does not match expected evidence: " + expected.path());
			}
			if (expected.expectedSha256() != null && !expected.expectedSha256().equals(hash)) {
				throw new IOException("Captured digest does not match expected evidence: " + expected.path());
			}
			Reference.Captured captured = new Reference.Captured(expected.fileName(), hash, size);
			observer.verified(captured);
			return captured;
		} catch (IOException failure) {
			observer.failed(failure);
			throw failure;
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("The Java runtime must provide SHA-256.", failure);
		}
	}

	/**
	 * Progress of one capture.
	 *
	 * @param transferredSize bytes read so far
	 * @param expectedSize    total expected bytes, when known
	 */
	record Transfer(long transferredSize, @NonNull OptionalLong expectedSize) {

		/**
		 * Checks non-negative counts.
		 */
		public Transfer {
			if (transferredSize < 0) {
				throw new IllegalArgumentException("Transferred bytes must be non-negative");
			}
			if (expectedSize.isPresent() && expectedSize.getAsLong() < 0) {
				throw new IllegalArgumentException("Expected bytes must be non-negative or empty");
			}
		}
	}
}

/**
 * Single-use description of a caller-owned stream, which stays open.
 *
 * @param of     expected identity
 * @param source caller-owned stream
 * @param opened whether the stream was already handed out
 */
record StreamSource(Reference.Pending of, InputStream source, AtomicBoolean opened) implements Describe {

	@Override
	public InputStream open() throws IOException {
		if (opened.getAndSet(true)) {
			throw new IOException("A captured stream can only be read once.");
		}
		return new FilterInputStream(source) {
			@Override
			public void close() {
			}
		};
	}
}

/**
 * Reusable description of a private copy of bytes.
 *
 * @param of    expected identity
 * @param bytes private copy
 */
record BytesSource(Reference.Pending of, byte[] bytes) implements Describe {

	@Override
	public InputStream open() {
		return new ByteArrayInputStream(bytes);
	}
}