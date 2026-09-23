package cat.tabbie.sdk.album;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalLong;

import lombok.NonNull;

/**
 * Opens source content and retains immutable captured bytes for a specific
 * item. Implementations may acquire remote, local, cached, or generated
 * content. Each successful capture publishes complete bytes; an I/O failure
 * publishes no incomplete content.
 *
 * Captured references are verified by digest and size. Stores may deduplicate
 * bytes across filenames. Retention implementations keep bytes readable until
 * their documented lifetime ends or their owner explicitly reclaims them. Core
 * owns revision/backup retention. Providers document concurrency and limits;
 * this interface supplies no backend. Sources supply open() and, for named-file
 * placement, fileName(). Known catalog checksums are verified by the source;
 * retention stores implement capture, open(reference), and exists(reference).
 */
public interface Store extends AutoCloseable {

	/**
	 * Describes this store's own primary content without opening it.
	 *
	 * @return expected identity of this store's primary content
	 */
	Reference.Pending of();

	/**
	 * Opens provider-designated primary content, when the store has one.
	 *
	 * @param source expected identity of the content being opened
	 * @return independent stream at position zero, owned by the caller
	 * @throws IOException when no primary content is designated or readable
	 */
	@NonNull
	InputStream open(Reference.Pending source) throws IOException;

	/**
	 * Checks whether primary content is already locally available, without
	 * acquiring it. Sources without local retention return false by default.
	 * This is an observation, not a reservation or an installation-state check.
	 *
	 * @return whether primary content is locally available
	 * @throws IOException when availability cannot be checked
	 */
	default boolean exists() throws IOException {
		return false;
	}

	/**
	 * Consumes the source to EOF and retains complete bytes, leaving the source
	 * open. Reports progress for this transfer and then verification or an I/O
	 * failure. Observer callbacks must not throw; an unchecked callback failure
	 * propagates and does not imply retained content was rolled back. The
	 * produced reference is cross-checked against this store's own {@link #of()}
	 * evidence when it declares an expected digest or size.
	 *
	 * @param source   caller-owned stream
	 * @param observer transfer callbacks
	 * @return verified reference to retained bytes
	 * @throws IOException when reading or retention fails
	 */
	@NonNull
	default Intermediate<Reference> capture(@NonNull InputStream source, @NonNull Observer observer)
			throws IOException {
		return new Capture(of(), source, observer);
	}

	/**
	 * Captures bytes without observing progress or closing the source.
	 *
	 * @param source caller-owned stream
	 * @return verified reference to retained bytes
	 * @throws IOException when reading or retention fails
	 */
	@NonNull
	default Intermediate<Reference> capture(@NonNull InputStream source) throws IOException {
		return capture(source, Observer.NONE);
	}

	/**
	 * Captures a defensive copy of caller-owned bytes.
	 *
	 * @param bytes complete content
	 * @return retained content reference
	 * @throws IOException when retention fails
	 */
	@NonNull
	default Intermediate<Reference> capture(@NonNull byte[] bytes) throws IOException {
		return capture(new ByteArrayInputStream(bytes.clone()));
	}

	/**
	 * Releases store resources according to its documented lifetime. Artists
	 * never close caller-owned stores. Stateless sources need no cleanup.
	 *
	 * @throws IOException when resources cannot be released
	 */
	@Override
	default void close() throws IOException {
	}

	/**
	 * A reusable, inspectable description of how to produce derived bytes.
	 * Describing performs no I/O; {@link #open()} is repeatable and independent
	 * of any prior inspection, so a description can be reopened later without
	 * retaining an open handle to whatever it was derived from.
	 */
	interface Describe {

		/**
		 * Describes the derived bytes without opening them.
		 *
		 * @return expected identity of the derived content
		 */
		Reference.Pending of();

		/**
		 * Opens fresh content independently of prior inspection resources.
		 *
		 * @return caller-owned stream
		 * @throws IOException when opening fails
		 */
		InputStream open() throws IOException;
	}

	/**
	 * A retention backend capable of capturing an arbitrary described source
	 * under that source's own identity, rather than this backend's own.
	 */
	interface Extract {

		/**
		 * Checks whether content matching the given description is already
		 * retained, without acquiring it.
		 *
		 * @param source expected identity to check for
		 * @return whether matching content is already retained
		 * @throws IOException when availability cannot be checked
		 */
		boolean exists(Reference.Pending source) throws IOException;

		/**
		 * Captures a described source, verifying and retaining its bytes under
		 * its own identity. The default opens the source only when the returned
		 * intermediate is collapsed.
		 *
		 * @param source reusable description of the bytes to capture
		 * @return verified reference to retained bytes
		 */
		@NonNull
		default Intermediate<Reference.Captured> capture(@NonNull Describe source) {
			return new Extraction(source);
		}
	}

	/**
	 * Capture effect over an already-open, caller-owned stream.
	 *
	 * @param pending  expected identity to verify against, if it declares one
	 * @param source   caller-owned stream, opened before construction
	 * @param observer transfer callbacks
	 */
	record Capture(Reference.Pending pending, InputStream source, Observer observer)
			implements Intermediate.Step<Reference> {
		@Override
		public Reference collapse(Intermediate.Context context) throws IOException {
			return digest(pending, source, observer);
		}
	}

	/**
	 * Capture effect over a reusable description, opened only during collapse.
	 *
	 * @param source reusable description of the bytes to capture
	 */
	record Extraction(Describe source) implements Intermediate.Step<Reference.Captured> {
		@Override
		public Reference.Captured collapse(Intermediate.Context context) throws IOException {
			try (InputStream input = source.open()) {
				return digest(source.of(), input, context.observer());
			}
		}
	}

	/**
	 * Reads a stream to EOF, computing its SHA-256 digest and size, verifying
	 * against any expected evidence the description declares.
	 *
	 * @param pending  expected identity to verify against, if it declares one
	 * @param source   stream to consume to EOF
	 * @param observer transfer callbacks
	 * @return verified reference to the consumed bytes
	 * @throws IOException when reading fails or verification does not match
	 */
	private static Reference.Captured digest(Reference.Pending pending, InputStream source, Observer observer)
			throws IOException {
		try {
			MessageDigest digest = sha256();
			long size = 0;
			byte[] buffer = new byte[8192];
			for (int read; (read = source.read(buffer)) != -1;) {
				digest.update(buffer, 0, read);
				size = Math.addExact(size, read);
				observer.transferred(size,
						pending.expectedSize() == null ? OptionalLong.empty() : OptionalLong.of(pending.expectedSize()));
			}
			String hash = HexFormat.of().formatHex(digest.digest());
			if (pending.expectedSize() != null && pending.expectedSize() != size)
				throw new IOException("Captured size does not match expected evidence.");
			if (pending.expectedSha256() != null && !pending.expectedSha256().equals(hash))
				throw new IOException("Captured digest does not match expected evidence.");
			Reference.Captured captured = new Reference.Captured(pending.fileName(), hash, size);
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
}
