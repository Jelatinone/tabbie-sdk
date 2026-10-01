package cat.tabbie.sdk.album.repository;

import java.io.IOException;
import java.io.InputStream;

import lombok.NonNull;

/**
 * The source role: a reusable, inspectable description of how to obtain
 * bytes. Providers describe artifact content this way; retention backends
 * describe retained content this way too. Describing performs no I/O;
 * {@link #open} is repeatable and independent of any prior inspection, so a
 * description can be reopened later without retaining an open handle to
 * whatever it was derived from.
 */
public interface Describe {

	/**
	 * Describes the content without opening it.
	 *
	 * @return expected identity of the content
	 */
	@NonNull
	Reference.Pending of();

	/**
	 * Opens the described content.
	 *
	 * @return independent stream at position zero, owned by the caller
	 * @throws IOException when the content is not readable
	 */
	@NonNull
	InputStream open() throws IOException;
}