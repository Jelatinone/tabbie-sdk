package cat.tabbie.sdk.album;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import lombok.NonNull;

/**
 * <h2>Store</h2>
 *
 * Opens source content and retains immutable captured bytes. Implementations
 * may acquire remote, local, cached, or generated content. Each successful
 * capture publishes complete bytes; an I/O failure publishes no incomplete
 * content.
 *
 * Captured references are verified by digest and size. Pending references
 * select provider-owned named content with the declared size, without asserting
 * a digest. Stores may deduplicate bytes across filenames. Providers document
 * content lifetime, concurrency, and any limits; this interface supplies no
 * backend.
 */
public interface Store extends AutoCloseable {

	/**
	 * Opens provider-designated primary content, when the store has one.
	 *
	 * @return independent stream at position zero, owned by the caller
	 * @throws IOException when no primary content is designated or readable
	 */
	@NonNull
	InputStream open() throws IOException;

	/**
	 * Consumes the source to EOF and retains complete bytes, leaving the source
	 * open. Reports progress for this transfer and then verification or an I/O
	 * failure. Observer callbacks must not throw; an unchecked callback failure
	 * propagates and does not imply retained content was rolled back.
	 *
	 * @param source   caller-owned stream
	 * @param observer transfer callbacks
	 * @return verified reference to retained bytes
	 * @throws IOException when reading or retention fails
	 */
	@NonNull
	Reference capture(@NonNull InputStream source, @NonNull Observer observer) throws IOException;

	/**
	 * Captures bytes without observing progress or closing the source.
	 *
	 * @param source caller-owned stream
	 * @return verified reference to retained bytes
	 * @throws IOException when reading or retention fails
	 */
	@NonNull
	default Reference capture(@NonNull InputStream source) throws IOException {
		return capture(source, Observer.NONE);
	}

	/**
	 * Captures a local file, deriving its filename and closing the opened stream.
	 *
	 * @param source file to read
	 * @return verified reference to retained bytes
	 * @throws IOException              when opening, reading, closing, or retention
	 *                                  fails
	 * @throws IllegalArgumentException when the path has no filename
	 */
	@NonNull
	default Reference capture(@NonNull Path source) throws IOException {
		try (InputStream input = Files.newInputStream(source)) {
			return capture(input);
		}
	}

	/**
	 * Captures a defensive copy of caller-owned bytes.
	 *
	 * @param bytes complete content
	 * @return retained content reference
	 * @throws IOException when retention fails
	 */
	@NonNull
	default Reference capture(@NonNull byte[] bytes) throws IOException {
		return capture(new ByteArrayInputStream(bytes.clone()));
	}

	/**
	 * <h2>Observer</h2>
	 * 
	 * Releases this store's resources according to its documented lifetime.
	 * Artifact image generation never closes caller-owned stores.
	 *
	 * @throws IOException when resources cannot be released
	 */
	interface Observer {

		/**
		 * Observer that discards every notification.
		 */
		Observer NONE = new Observer() {
		};

		/**
		 * Observe in-progress transfer statistics
		 * 
		 * @param transferredSize current total bytes transferred
		 * @param expectedSize    expected total bytes transferred
		 */
		default void transferred(long transferredSize, @NonNull OptionalLong expectedSize) {
		}

		/**
		 * Observe complete transfer verification of a capture
		 * 
		 * @param reference verified capture reference
		 */
		default void verified(Reference reference) {
		}

		/**
		 * Observe failed transfer verification of a capture
		 * 
		 * @param failure failed capture exception
		 */
		default void failed(IOException failure) {
		}
	}
}
