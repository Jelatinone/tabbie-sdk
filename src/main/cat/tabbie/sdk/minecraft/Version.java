package cat.tabbie.sdk.minecraft;

import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.NonNull;

/**
 * Represents a specific version of Minecraft.
 *
 * <p>
 * A version is identified by its edition and a normalized, lowercase
 * identifier, and may include metadata describing its release type and release
 * date. Identity comparisons use {@link #match(Version)}, which ignores that
 * metadata; record equality does not.
 *
 * <p>
 * Version discovery and loading are intentionally handled externally. This
 * type only models versions that have already been discovered.
 */
public sealed interface Version
    permits Version.Java, Version.Bedrock {

  /**
   * Loosely written version text accepted by {@link #interpret(String)}: an
   * optional {@code minecraft} prefix, an optional {@code java} or
   * {@code bedrock} edition (optionally followed by {@code edition}), each
   * separated by whitespace or a colon, then the identifier. Matching ignores
   * case.
   */
  Pattern VERSION_PATTERN = Pattern.compile(
      "(?i)^(?:minecraft(?:\\s*:\\s*|\\s+))?(?:(java|bedrock)(?:\\s+edition)?(?:\\s*:\\s*|\\s+))?(.+)$");

  /**
   * Orders versions of one edition by {@link #number() release number}, then
   * {@link #releaseDate() release date}, as
   * {@link cat.tabbie.sdk.merchant.Release#PUBLICATION_ORDER} orders
   * releases. A version without a number, such as a week snapshot, orders
   * before every numbered version, and one without a known date before every
   * dated version of the same number, so a pre-release orders before its
   * release only when both dates are known.
   *
   * @apiNote Comparing versions of different editions throws
   *          {@link IllegalArgumentException}.
   */
  Comparator<Version> RELEASE_ORDER = (first, second) -> {
    if (first.getClass() != second.getClass()) {
      throw new IllegalArgumentException("Versions of different editions are not ordered.");
    }
    return Comparator.<Version, Number>comparing(version -> version.number().orElse(null),
        Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(version -> version.releaseDate().orElse(null), Comparator.nullsFirst(Comparator.naturalOrder()))
        .compare(first, second);
  };

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
   * Returns this version's release number, read from the numbers its
   * identifier begins with: {@code [1, 21, 4]} for {@code 1.21.4}, or
   * {@code [26, 3]} with no patch for {@code 26.3}. A fourth number, as some
   * Bedrock identifiers carry, is not part of it. A pre-release shares the
   * number of the release it leads to, such as {@code 1.21} for
   * {@code 1.21-pre1}, and is told apart by its release date.
   *
   * @return release number, or empty when the identifier does not begin with
   *         one, such as the week snapshot {@code 24w14a}
   */
  @NonNull
  default Optional<Number> number() {
    return Number.of(id());
  }

  /**
   * A version's release number: its major, minor and patch numbers. A
   * version such as {@code 26.3} has no patch, which orders before every
   * patch, so {@code 26.3} orders before {@code 26.3.1}.
   *
   * @param major major number
   * @param minor minor number
   * @param patch patch number, or {@link #ABSENT} when the version has none
   */
  record Number(int major, int minor, int patch) implements Comparable<Number> {

    /**
     * The patch of a number written with two parts.
     */
    public static final int ABSENT = -1;

    /**
     * Leading numbers of an identifier: a major and a minor number, then an
     * optional patch, each separated by a dot.
     */
    private static final Pattern LEADING = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /**
     * Checks the numbers are not negative, other than an absent patch.
     *
     * @throws IllegalArgumentException when a number is negative
     */
    public Number {
      if (major < 0 || minor < 0 || patch < ABSENT) {
        throw new IllegalArgumentException("A release number's parts are not negative.");
      }
    }

    /**
     * A number written with two parts, such as {@code 26.3}.
     *
     * @param major major number
     * @param minor minor number
     */
    public Number(int major, int minor) {
      this(major, minor, ABSENT);
    }

    /**
     * Reads the release number an identifier begins with.
     *
     * @param id version identifier, such as {@code 1.21.4} or {@code 26.3}
     * @return release number, or empty when the identifier does not begin
     *         with one
     */
    public static Optional<Number> of(@NonNull String id) {
      var matcher = LEADING.matcher(id);
      if (!matcher.find()) {
        return Optional.empty();
      }
      return Optional.of(new Number(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
          matcher.group(3) == null ? ABSENT : Integer.parseInt(matcher.group(3))));
    }

    @Override
    public int compareTo(@NonNull Number other) {
      return Comparator.comparingInt(Number::major)
          .thenComparingInt(Number::minor)
          .thenComparingInt(Number::patch)
          .compare(this, other);
    }

    /**
     * Writes the number as its identifier does, with two parts when it has no
     * patch.
     *
     * @return such as {@code 1.21.4} or {@code 26.3}
     */
    @Override
    public String toString() {
      return patch == ABSENT ? major + "." + minor : major + "." + minor + "." + patch;
    }
  }

  /**
   * A discovered Java Edition version.
   *
   * @param id          canonical identifier
   * @param releaseType edition-specific release classification
   * @param releaseDate release instant, or empty when unknown
   */
  record Java(
      @NonNull String id,
      @NonNull Java.Release releaseType,
      @NonNull Optional<Instant> releaseDate) implements Version {

    /**
     * Normalizes the edition-specific identifier.
     *
     * @throws IllegalArgumentException when the identifier is blank, contains
     *                                  whitespace, or is qualified with
     *                                  {@code :} or {@code /}
     */
    public Java {
      id = normalize(id);
    }

    /**
     * Describes a version with a known release classification and date.
     *
     * @param id          canonical identifier
     * @param releaseType edition-specific release classification
     * @param releaseDate known release instant
     */
    public Java(@NonNull String id, @NonNull Java.Release releaseType, @NonNull Instant releaseDate) {
      this(id, releaseType, Optional.of(releaseDate));
    }

    /**
     * Describes a version with a known release classification and an unknown
     * date.
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

      /**
       * A full release.
       */
      RELEASE,

      /**
       * A pre-release ahead of a full release.
       */
      PRE_RELEASE,

      /**
       * A release candidate ahead of a full release.
       */
      RELEASE_CANDIDATE,

      /**
       * A weekly development snapshot.
       */
      SNAPSHOT,

      /**
       * A historical beta version.
       */
      BETA,

      /**
       * A historical alpha version.
       */
      ALPHA,

      /**
       * No classification evidence, such as a parsed canonical version.
       */
      UNKNOWN
    }

    /**
     * Checks whether a version belongs to Java Edition.
     *
     * @param version candidate version
     * @return whether the version is a Java Edition version
     */
    public static boolean applicable(@NonNull Version version) {
      return version instanceof Java;
    }
  }

  /**
   * A discovered Bedrock Edition version.
   *
   * @param id          canonical identifier
   * @param releaseType edition-specific release classification
   * @param releaseDate release instant, or empty when unknown
   */
  record Bedrock(
      @NonNull String id,
      @NonNull Bedrock.Release releaseType,
      @NonNull Optional<Instant> releaseDate) implements Version {

    /**
     * Normalizes the edition-specific identifier.
     *
     * @throws IllegalArgumentException when the identifier is blank, contains
     *                                  whitespace, or is qualified with
     *                                  {@code :} or {@code /}
     */
    public Bedrock {
      id = normalize(id);
    }

    /**
     * Describes a version with a known release classification and date.
     *
     * @param id          canonical identifier
     * @param releaseType edition-specific release classification
     * @param releaseDate known release instant
     */
    public Bedrock(@NonNull String id, @NonNull Bedrock.Release releaseType, @NonNull Instant releaseDate) {
      this(id, releaseType, Optional.of(releaseDate));
    }

    /**
     * Describes a version with a known release classification and an unknown
     * date.
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

      /**
       * A full release.
       */
      RELEASE,

      /**
       * A preview build ahead of a full release.
       */
      PREVIEW,

      /**
       * A beta build.
       */
      BETA,

      /**
       * An alpha build.
       */
      ALPHA,

      /**
       * No classification evidence, such as a parsed canonical version.
       */
      UNKNOWN
    }

    /**
     * Checks whether a version belongs to Bedrock Edition.
     *
     * @param version candidate version
     * @return whether the version is a Bedrock Edition version
     */
    public static boolean applicable(@NonNull Version version) {
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
