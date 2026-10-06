package cat.tabbie.sdk.minecraft;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.NonNull;

/**
 * Represents a specific version of Minecraft.
 *
 * A version is identified by its edition-specific identifier and may include
 * metadata describing its release type and release date.
 *
 * Version discovery and loading are intentionally handled externally. This
 * type only models versions that have already been discovered.
 */
public sealed interface Version
		permits Version.Java, Version.Bedrock {

	Pattern VERSION_PATTERN = Pattern.compile(
			"(?i)^(?:minecraft(?:\\s*:\\s*|\\s+))?(?:(java|bedrock)(?:\\s+edition)?(?:\\s*:\\s*|\\s+))?(.+)$");

	/**
	 * The canonical identifier for this version. Examples include {@code 1.20.1},
	 * {@code 26.2}, or {@code 1.21.80}.
	 *
	 * @return canonical identifier
	 */
	@NonNull
	String id();

	/**
	 * The time at which this version was released, if known.
	 *
	 * @return time of release
	 */
	@NonNull
	Optional<Instant> releaseDate();

	/**
	 * Returns stable text naming the edition and identifier, such as
	 * {@code java:1.21.1} or {@code bedrock:1.21.80}, for persistence. Versions
	 * that {@link #match(Version)} share canonical text.
	 *
	 * @return canonical version text
	 */
	default String canonical() {
		return switch (this) {
			case Java java -> "java:" + java.id();
			case Bedrock bedrock -> "bedrock:" + bedrock.id();
		};
	}

	/**
	 * Parses text produced by {@link #canonical()}. Release metadata is not part
	 * of the canonical text, so the result has an unknown release type and date.
	 *
	 * @param canonical canonical version text such as {@code java:1.21.1}
	 * @return version with unknown release metadata
	 * @throws IllegalArgumentException when the text is not canonical
	 */
	static Version parse(@NonNull String canonical) {
		int separator = canonical.indexOf(':');
		String edition = separator < 0 ? "" : canonical.substring(0, separator);
		String id = canonical.substring(separator + 1);
		Version version = switch (edition) {
			case "java" -> new Java(id, Java.Release.UNKNOWN);
			case "bedrock" -> new Bedrock(id, Bedrock.Release.UNKNOWN);
			default -> throw new IllegalArgumentException("Not canonical version text: " + canonical);
		};
		if (!version.canonical().equals(canonical)) {
			throw new IllegalArgumentException("Not canonical version text: " + canonical);
		}
		return version;
	}

	/**
	 * Interprets loosely written version text, such as user input. Text naming
	 * no edition is read as Java Edition.
	 *
	 * @param text version text matching {@link #VERSION_PATTERN}
	 * @return version with unknown release metadata
	 * @throws IllegalArgumentException when no valid identifier remains
	 */
	static Version interpret(@NonNull String text) {
		var matcher = VERSION_PATTERN.matcher(text.strip());
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Not version text: " + text);
		}
		String edition = matcher.group(1) == null ? "java" : matcher.group(1).toLowerCase(Locale.ROOT);
		return edition.equals("bedrock")
				? new Bedrock(matcher.group(2), Bedrock.Release.UNKNOWN)
				: new Java(matcher.group(2), Java.Release.UNKNOWN);
	}

	/**
	 * Compares edition and normalized identifier independently of release metadata.
	 *
	 * @param other discovered version
	 * @return whether edition and identifier agree
	 */
	default boolean match(@NonNull Version other) {
		return getClass() == other.getClass() && id().equals(other.id());
	}

	/**
	 *
	 * A discovered Java Edition version.
	 *
	 * @param id          canonical identifier
	 * @param releaseType edition-specific release classification
	 * @param releaseDate known release instant
	 */
	record Java(
			@NonNull String id,
			@NonNull Java.Release releaseType,
			@NonNull Optional<Instant> releaseDate) implements Version {

		/**
		 * Normalizes the edition-specific identifier.
		 */
		public Java {
			id = normalize(id);
		}

		/**
		 * Describes a veeersion with a known release intent.
		 * 
		 * @param id          canonical identifier
		 * @param releaseType edition-specific release classification
		 * @param releaseDate known release instance
		 */
		public Java(@NonNull String id, @NonNull Java.Release releaseType, @NonNull Instant releaseDate) {
			this(id, releaseType, Optional.of(releaseDate));
		}

		/**
		 * Describes a veeersion with a known release intent.
		 * 
		 * @param id          canonical identifier
		 * @param releaseType edition-specific release classification
		 */
		public Java(@NonNull String id, @NonNull Java.Release releaseType) {
			this(id, releaseType, Optional.empty());
		}

		/**
		 * Provider-reported release classification, independent of version identity.
		 */
		public enum Release {

			RELEASE,

			PRE_RELEASE,

			RELEASE_CANDIDATE,

			SNAPSHOT,

			BETA,

			ALPHA,

			UNKNOWN
		}

		/**
		 * Whether a version is applicable to this version kind
		 *
		 * @param version version target
		 * @return whether a version is applicable
		 */
		public static boolean applicable(Version version) {
			return version instanceof Java;
		}
	}

	/**
	 * A discovered Bedrock Edition version.
	 *
	 * @param id          canonical identifier
	 * @param releaseType edition-specific release classification
	 * @param releaseDate known release instant
	 */
	record Bedrock(
			@NonNull String id,
			@NonNull Bedrock.Release releaseType,
			@NonNull Optional<Instant> releaseDate) implements Version {

		/**
		 * Normalizes the edition-specific identifier.
		 */
		public Bedrock {
			id = normalize(id);
		}

		/**
		 * Describes a veeersion with a known release intent.
		 * 
		 * @param id          canonical identifier
		 * @param releaseType edition-specific release classification
		 * @param releaseDate known release instance
		 */
		public Bedrock(@NonNull String id, @NonNull Bedrock.Release releaseType, @NonNull Instant releaseDate) {
			this(id, releaseType, Optional.of(releaseDate));
		}

		/**
		 * Describes a veeersion with a known release intent.
		 * 
		 * @param id          canonical identifier
		 * @param releaseType edition-specific release classification
		 */
		public Bedrock(@NonNull String id, @NonNull Bedrock.Release releaseType) {
			this(id, releaseType, Optional.empty());
		}

		/**
		 * Provider-reported release classification, independent of version identity.
		 */
		public enum Release {

			RELEASE,

			PREVIEW,

			BETA,

			ALPHA,

			UNKNOWN
		}

		/**
		 * Whether a version is applicable to this version kind
		 *
		 * @param version version target
		 * @return whether a version is applicable
		 */
		public static boolean applicable(Version version) {
			return version instanceof Bedrock;
		}
	}

	private static String normalize(@NonNull String id) {
		String result = id.strip().toLowerCase(Locale.ROOT);
		if (result.isEmpty()
				|| result.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isSpaceChar(character))
				|| result.indexOf(':') >= 0 || result.indexOf('/') >= 0) {
			throw new IllegalArgumentException("Version identifiers must be non-blank and unqualified.");
		}
		return result;
	}
}
