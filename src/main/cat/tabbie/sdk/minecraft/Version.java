package cat.tabbie.sdk.minecraft;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	static final Pattern VERSION_PATTERN = Pattern.compile(
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
	Instant releaseDate();

	/**
	 * Matches an external identifier with optional Minecraft and edition prefixes.
	 * Explicitly naming the other edition never matches. Only leading qualifiers
	 * are removed; arbitrary identifier substrings remain meaningful.
	 *
	 * @param id external identifier
	 * @return whether the identifier names this version
	 */
	default boolean match(@NonNull String id) {
		Matcher matcher = VERSION_PATTERN.matcher(id.strip());
		if (!matcher.matches()) {
			return false;
		}
		String edition = matcher.group(1);
		return (edition == null || edition.equalsIgnoreCase(this instanceof Java ? "java" : "bedrock"))
				&& matcher.group(2).strip().equalsIgnoreCase(id());
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
			@NonNull Instant releaseDate) implements Version {

		public Java {
			id = normalize(id);
		}

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
			@NonNull Instant releaseDate) implements Version {

		public Bedrock {
			id = normalize(id);
		}

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
		if (result.isEmpty() || result.chars().anyMatch(Character::isWhitespace) || result.indexOf(':') >= 0) {
			throw new IllegalArgumentException("Version identifiers must be non-blank and unqualified.");
		}
		return result;
	}
}
