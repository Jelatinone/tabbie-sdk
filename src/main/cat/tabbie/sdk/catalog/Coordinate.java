package cat.tabbie.sdk.catalog;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import lombok.NonNull;

/**
 * Provider-scoped catalog coordinates, nested project, channel, release, and
 * file. These values carry enough provider information to query the same
 * project, channel, release, or file again. They contain no credentials,
 * provider instances, or transport assumptions. Keys are opaque to everything
 * except the owning provider.
 *
 * <p>
 * Typical mappings:
 * <ul>
 * <li>Mojang: project {@code minecraft}, channel {@code release}, build
 * {@code 1.21.1}, file {@code client}</li>
 * <li>Paper: project {@code paper}, channel {@code default}, build
 * {@code 1.21.1-130}, file {@code server}</li>
 * <li>Modrinth: project ID, version type, version ID, file hash or name</li>
 * <li>CurseForge: project ID, release type, file ID, file ID (one file per
 * release)</li>
 * </ul>
 */
public sealed interface Coordinate {

  /**
   * Returns the owning provider identity.
   *
   * @return stable provider identity
   */
  @NonNull
  Identity<Provider<?, ?>> providerId();

  /**
   * Returns this level's opaque provider key.
   *
   * @return non-blank key
   */
  @NonNull
  String key();

  /**
   * Returns the project this coordinate belongs to, or itself for a project.
   *
   * @return owning project coordinates
   */
  @NonNull
  Project project();

  /**
   * Checks whether the other coordinate is this coordinate or addressed beneath
   * it. A project includes its channels, releases, and files; a channel
   * includes its releases and their files; a release includes its files; and
   * a file includes only itself. Dependency and conflict declarations match
   * candidate content by this rule.
   *
   * @param other candidate coordinate
   * @return whether this coordinate includes the other
   */
  default boolean includes(@NonNull Coordinate other) {
    return switch (this) {
      case Project project -> project.equals(other.project());
      case Channel channel -> channel.equals(other)
          || other instanceof Build build && channel.equals(build.channel())
          || other instanceof File file && channel.equals(file.build().channel());
      case Build build -> build.equals(other) || other instanceof File file && build.equals(file.build());
      case File file -> file.equals(other);
    };
  }

  /**
   * Returns stable, unambiguous text of the form
   * {@code provider/project[/channel[/build[/file]]]}, with the provider UUID
   * followed by each key URL-encoded as UTF-8. Equal coordinates, and only equal
   * coordinates, share canonical text, so it is suitable for persistence and
   * for deriving identities with {@link Identity#create(String)}.
   *
   * @return canonical coordinate text
   */
  default String canonical() {
    return switch (this) {
      case Project project -> project.providerId().id() + "/" + encode(project.key());
      case Channel channel -> channel.project().canonical() + "/" + encode(channel.key());
      case Build build -> build.channel().canonical() + "/" + encode(build.key());
      case File file -> file.build().canonical() + "/" + encode(file.key());
    };
  }

  /**
   * Parses canonical coordinate text produced by {@link #canonical()}. Only
   * canonical text is accepted, so parsing and printing round-trip exactly.
   *
   * @param canonical canonical coordinate text
   * @return project, channel, release, or file coordinates
   * @throws IllegalArgumentException when the text is not canonical
   */
  static Coordinate parse(@NonNull String canonical) {
    String[] parts = canonical.split("/", -1);
    if (parts.length < 2 || parts.length > 5) {
      throw new IllegalArgumentException("Not canonical coordinate text: " + canonical);
    }
    Identity<Provider<?, ?>> providerId = Identity.create(UUID.fromString(parts[0]));
    Project project = new Project(providerId, decode(parts[1]));
    Coordinate result = switch (parts.length) {
      case 2:
        yield project;
      case 3:
        yield project.channel(decode(parts[2]));
      case 4:
        yield project.channel(decode(parts[2])).build(decode(parts[3]));
      case 5:
        yield project.channel(decode(parts[2])).build(decode(parts[3])).file(decode(parts[4]));
      default:
        throw new IllegalArgumentException("Not canonical coordinate text: " + canonical);
    };
    if (!result.canonical().equals(canonical)) {
      throw new IllegalArgumentException("Not canonical coordinate text: " + canonical);
    }
    return result;
  }

  /**
   * A catalog project independent of its releases.
   *
   * @param providerId owning provider identity
   * @param key        exact project key
   */
  record Project(@NonNull Identity<Provider<?, ?>> providerId, @NonNull String key) implements Coordinate {

    /**
     * Validates the opaque non-blank project key.
     */
    public Project {
      validate(key);
    }

    @Override
    public Project project() {
      return this;
    }

    /**
     * Addresses a release channel of this project.
     *
     * @param channelKey exact channel key
     * @return channel coordinates
     * @throws IllegalArgumentException when the key is blank
     */
    public Channel channel(@NonNull String channelKey) {
      return new Channel(this, channelKey);
    }
  }

  /**
   * A provider release channel, such as stable or beta releases of a project.
   *
   * @param project owning project
   * @param key     exact channel key
   */
  record Channel(@NonNull Project project, @NonNull String key) implements Coordinate {

    /**
     * Validates the channel key.
     */
    public Channel {
      validate(key);
    }

    @Override
    public Identity<Provider<?, ?>> providerId() {
      return project.providerId();
    }

    /**
     * Addresses an exact release published on this channel.
     *
     * @param buildKey exact release key
     * @return release coordinates
     * @throws IllegalArgumentException when the key is blank
     */
    public Build build(@NonNull String buildKey) {
      return new Build(this, buildKey);
    }
  }

  /**
   * An exact provider release.
   *
   * @param channel owning channel
   * @param key     exact release key
   */
  record Build(@NonNull Channel channel, @NonNull String key) implements Coordinate {

    /**
     * Validates the release key.
     */
    public Build {
      validate(key);
    }

    @Override
    public Identity<Provider<?, ?>> providerId() {
      return channel.providerId();
    }

    /**
     * Addresses an exact payload of this release.
     *
     * @param fileKey provider file key, not necessarily a filesystem name
     * @return file coordinates
     * @throws IllegalArgumentException when the key is blank
     */
    public File file(@NonNull String fileKey) {
      return new File(this, fileKey);
    }

    /**
     * Returns the project owning this release's channel.
     *
     * @return owning project
     */
    public Project project() {
      return channel().project();
    }
  }

  /**
   * An exact payload within a release.
   *
   * @param build owning release
   * @param key   provider file key, not necessarily a filesystem name
   */
  record File(@NonNull Build build, @NonNull String key) implements Coordinate {

    /**
     * Validates the payload key.
     */
    public File {
      validate(key);
    }

    @Override
    public Identity<Provider<?, ?>> providerId() {
      return build.providerId();
    }

    /**
     * Returns the project owning this payload's release.
     *
     * @return owning project
     */
    public Project project() {
      return build.project();
    }
  }

  /**
   * Checks a coordinate key for construction.
   *
   * @param key candidate key
   * @throws IllegalArgumentException when the key is blank
   */
  private static void validate(String key) {
    if (key.isBlank()) {
      throw new IllegalArgumentException("Provider coordinates must be non-blank.");
    }
    // An unpaired surrogate encodes as '?', so two keys would share canonical text
    if (key.codePoints().anyMatch(point -> point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)) {
      throw new IllegalArgumentException("Provider coordinates must be well-formed Unicode.");
    }
  }

  /**
   * Escapes an opaque key so separators in canonical text stay unambiguous.
   *
   * @param key opaque key
   * @return URL-encoded key
   */
  private static String encode(String key) {
    return URLEncoder.encode(key, StandardCharsets.UTF_8);
  }

  /**
   * Reverses {@link #encode(String)}.
   *
   * @param encoded URL-encoded key
   * @return opaque key
   */
  private static String decode(String encoded) {
    return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
  }
}
