package cat.tabbie.sdk.album;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import lombok.NonNull;

/**
 * <h1>Store</h1>
 *
 * Retains immutable content addressed by digest and size. Successful capture
 * publishes complete bytes atomically; a failed capture publishes nothing.
 * Implementations verify references, support independent repeated reads, and
 * keep content available for as long as their documented storage lifetime.
 */
public interface Store extends AutoCloseable {

	/**
	 * Opens verified content. The caller closes the returned independent stream.
	 * 
	 * @param reference retained content reference
	 * 
	 * @return readable content at position zero
	 * 
	 * @throws IOException when reading or retention fails
	 */
	@NonNull
	InputStream open(@NonNull Reference reference) throws IOException;

	/**
	 * Retains bytes without closing the caller-owned stream.
	 * 
	 * @param source   stream consumed to EOF
	 * @param observer transfer observer
	 * 
	 * @return retained content reference
	 * 
	 * @throws IOException when reading or retention fails
	 */
	@NonNull
	Reference.Captured capture(@NonNull String fileName,
			@NonNull InputStream source, @NonNull Observer observer) throws IOException;

	/**
	 * Retains bytes without closing the caller-owned stream.
	 *
	 * @param source stream consumed to EOF
	 * @return identity of the retained bytes
	 * 
	 * @throws IOException when reading or retention fails
	 */
	@NonNull
	default Reference.Captured capture(@NonNull String fileName, @NonNull InputStream source) throws IOException {
		return capture(fileName, source, Observer.NONE);
	}

	/**
	 * Captures a local file and closes the stream opened by this method.
	 *
	 * @param source file to read
	 * 
	 * @return retained content reference
	 * 
	 * @throws IOException when opening, reading, closing, or retention fails
	 */
	@NonNull
	default Reference.Captured capture(@NonNull Path source) throws IOException {
		try (InputStream input = Files.newInputStream(source)) {
			return capture(source.getFileName().toString(), input);
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
	default Reference.Captured capture(@NonNull String fileName, @NonNull byte[] bytes) throws IOException {
		return capture(fileName, new ByteArrayInputStream(bytes.clone()));
	}

	/**
	 * 
	 * <h2>Observer</h2>
	 * 
	 * Per-transfer callbacks to provide a caller with metrics. Byte counts concern
	 * this source artifact only, not several artifacts.
	 */
	interface Observer {

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
