package cat.tabbie.sdk.minecraft;

import java.time.Instant;
import java.util.Locale;

import lombok.NonNull;

/**
 * <h1>Version</h1>
 *
 * <p>
 * Represents a specific version of Minecraft.
 * </p>
 *
 * <p>
 * A version is identified by its edition-specific identifier and may include
 * metadata describing its release type and release date.
 * </p>
 *
 * <p>
 * Version discovery and loading are intentionally handled externally. This
 * type only models versions that have already been discovered.
 * </p>
 */
public sealed interface Version
		permits Version.Java, Version.Bedrock {

	/**
	 * The canonical identifier for this version.
	 *
	 * <p>
	 * Examples include {@code 1.20.1}, {@code 26.2}, or {@code 1.21.80}.
	 * </p>
	 * 
	 * @return canonical identifier
	 */
	@NonNull
	String id();

	/**
	 * The general release classification of this version.
	 * 
	 * @return release classification
	 */
	@NonNull
	Release type();

	/**
	 * The time at which this version was released, if known.
	 * 
	 * @return time of release
	 */
	@NonNull
	Instant released();

	/**
	 * Determines whether the supplied external version string refers to this
	 * version.
	 *
	 * <p>
	 * This is intended for adapting version identifiers supplied by external
	 * providers into a canonical version model.
	 * </p>
	 * 
	 * @return Whether this value refer to a version
	 */
	default boolean matches(@NonNull String value) {
		return normalize(value).equals(normalize(id()));
	}

	enum Release {

		RELEASE,

		SNAPSHOT,

		PRE_RELEASE,

		RELEASE_CANDIDATE,

		ALPHA,

		BETA,

		PREVIEW,

		UNKNOWN
	}

	/**
	 * Minecraft: Java Edition version.
	 */
	record Java(

			@NonNull String id,
			@NonNull Release type,
			@NonNull Instant released) implements Version {

		public Java {
			id = id.trim();

			if (id.isEmpty()) {
				throw new IllegalArgumentException("Version id cannot be empty.");
			}
		}
	}

	record Bedrock(

			@NonNull String id,
			@NonNull Release type,
			@NonNull Instant released) implements Version {

		public Bedrock {
			id = id.trim();

			if (id.isEmpty()) {
				throw new IllegalArgumentException("Version id cannot be empty.");
			}
		}
	}

	private static String normalize(@NonNull String value) {
		return value
				.trim()
				.toLowerCase(Locale.ROOT)
				.replace("minecraft:", "")
				.replace("minecraft", "")
				.replace("java edition", "")
				.replace("java", "")
				.replace("bedrock edition", "")
				.replace("bedrock", "")
				.trim();
	}
}