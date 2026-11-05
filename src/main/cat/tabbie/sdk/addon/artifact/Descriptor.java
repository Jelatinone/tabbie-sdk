package cat.tabbie.sdk.addon.artifact;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cat.tabbie.sdk.album.repository.Archive;
import cat.tabbie.sdk.album.revision.Chunk;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.layout.Lease;
import lombok.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A file content carries about itself, which is read but never edited, such
 * as the manifest a Bedrock pack declares its identity in. A descriptor is
 * found among the entries of the content's archive and read from there alone,
 * without unpacking anything, and knows where the content it describes lies
 * in that archive.
 */
public sealed interface Descriptor permits Descriptor.Manifest, Descriptor.Packmeta {

  /**
   * Returns the folder the descriptor's file lies in, which is where the
   * content it describes lies.
   *
   * @return folder of the described content, the root address for content not
   *         wrapped in a folder of its own
   */
  @NonNull
  Address root();

  /**
   * The header a Bedrock pack declares in its {@code manifest.json}: the
   * identity and version a World lists the pack by. Behaviour packs, resource
   * packs and world templates all carry one.
   *
   * @param root    folder the manifest lies in, where the pack's files are
   * @param uuid    pack identity
   * @param version pack version as the manifest writes it: three numbers, or
   *                a version string in newer manifest formats
   */
  record Manifest(@NonNull Address root, @NonNull UUID uuid, @NonNull JsonNode version) implements Descriptor {

    /**
     * Name of the file a pack declares its manifest in.
     */
    public static final String FILE = "manifest.json";

    /**
     * Copies the version, and checks it is three numbers or a version string.
     *
     * @throws IllegalArgumentException when the version is neither
     */
    public Manifest {
      version = version.deepCopy();
      boolean numbers = version.isArray() && version.size() == 3
          && version.values().stream().allMatch(JsonNode::isIntegralNumber);
      if (!numbers && !version.isString()) {
        throw new IllegalArgumentException("A pack's version is three numbers or a version string.");
      }
    }

    /**
     * Reads the header a pack's manifest declares.
     *
     * @param manifest the pack's manifest, at its path within the pack's
     *                 archive or directory
     * @return the declared identity and version
     * @throws Archive.Rejection.Malformed when the manifest is not JSON, or
     *                                     declares no uuid and version in its
     *                                     header
     * @throws IOException                 when the manifest cannot be read
     */
    public static Manifest of(@NonNull Describe manifest) throws IOException {
      JsonNode header;
      try {
        header = new Lease.Form.Json().readTree(manifest).path("header");
      } catch (Lease.Form.Unreadable unreadable) {
        throw new Archive.Rejection.Malformed("A pack's manifest is not readable JSON.", unreadable);
      }
      try {
        return new Manifest(manifest.path().parent().orElse(Address.root()),
            UUID.fromString(header.path("uuid").asString()), header.path("version"));
      } catch (IllegalArgumentException exception) {
        throw new Archive.Rejection.Malformed("A pack's manifest declares its uuid and version.", exception);
      }
    }

    /**
     * Finds and reads the one manifest among the entries of a pack's archive,
     * wherever in the archive it lies.
     *
     * @param entries file entries of the pack's archive
     * @return the declared identity and version, and where the pack lies
     * @throws Archive.Rejection.Malformed when the archive holds no manifest
     *                                     or several, as a bundle of packs
     *                                     does, or the manifest is malformed
     * @throws IOException                 when the manifest cannot be read
     */
    public static Manifest in(@NonNull List<Archive.Entry> entries) throws IOException {
      List<Archive.Entry> manifests = entries.stream()
          .filter(entry -> entry.path().fileName().equals(Optional.of(FILE)))
          .toList();
      if (manifests.size() != 1) {
        throw new Archive.Rejection.Malformed("A pack declares exactly one manifest.", null);
      }
      return of(manifests.getFirst());
    }

    /**
     * Checks the pack writes its version as three numbers, as a distribution
     * that reads no version string needs, such as every server of the Nukkit
     * family. Run before anything is placed, so a pack the distribution could
     * not load is never installed.
     *
     * @return this manifest
     * @throws Archive.Rejection.Unsupported when the version is a string, as
     *                                       manifest format 3 writes it
     */
    public Manifest requireNumbered() throws Archive.Rejection.Unsupported {
      if (version.isString()) {
        throw new Archive.Rejection.Unsupported(String.format("The manifest at %s writes the pack's version as a "
            + "string, which the distribution cannot read.", root.resolve(FILE).canonical()));
      }
      return this;
    }

    /**
     * Checks the manifest lies at most some folders deep in the pack, as a
     * distribution loading the pack's archive as it is needs. Run before
     * anything is placed, so a pack the distribution could not load is never
     * installed.
     *
     * @param depth most folders the manifest may lie in, zero for the pack's
     *              root
     * @return this manifest
     * @throws Archive.Rejection.Unsupported when it lies deeper
     */
    public Manifest requireDepth(int depth) throws Archive.Rejection.Unsupported {
      if (root.components().size() > depth) {
        throw new Archive.Rejection.Unsupported(String.format("The manifest at %s lies more than %d folders deep, "
            + "where the distribution does not find it.", root.resolve(FILE).canonical(), depth));
      }
      return this;
    }

    /**
     * Describes appending this pack's entry to a World's list of the packs it
     * loads, as the list's JSON form reads it. Its inverse removes the entry
     * wherever it is.
     *
     * @return chunk anchored at the end
     */
    @NonNull
    public Chunk listed() {
      Lease.Form.Json form = new Lease.Form.Json();
      ObjectNode entry = form.mapper().createObjectNode()
          .put("pack_id", uuid.toString())
          .set("version", version);
      try {
        return Chunk.append(Chunk.linesOf(form.decode(
            form.writeValue("packs.json", form.mapper().createArrayNode().add(entry)))));
      } catch (IOException exception) {
        throw new IllegalStateException("A list just written reads back.", exception);
      }
    }
  }

  /**
   * What a Java pack, a datapack or a resource pack, declares in its
   * {@code pack.mcmeta}: the pack formats it supports, and its description.
   * The formats are read from the newest fields the file declares:
   * {@code min_format} and {@code max_format}, each a major number or a major
   * and minor pair; otherwise {@code supported_formats}, a number, a
   * {@code [min, max]} pair or a {@code min_inclusive} and
   * {@code max_inclusive} object; otherwise the single {@code pack_format}. A
   * minor number left out is zero.
   *
   * @param root        folder the file lies in, where the pack's files are
   * @param oldest      oldest pack format the pack supports
   * @param newest      newest pack format the pack supports, no older than
   *                    the oldest
   * @param description the pack's description as the file writes it, a text
   *                    component, or a missing node when it declares none
   */
  record Packmeta(
      @NonNull Address root,
      @NonNull Format oldest,
      @NonNull Format newest,
      @NonNull JsonNode description) implements Descriptor {

    /**
     * Name of the file a Java pack declares itself in.
     */
    public static final String FILE = "pack.mcmeta";

    /**
     * Copies the description, and checks the formats are in order.
     *
     * @throws IllegalArgumentException when the newest format is older than
     *                                  the oldest
     */
    public Packmeta {
      description = description.deepCopy();
      if (newest.compareTo(oldest) < 0) {
        throw new IllegalArgumentException("A pack's newest supported format is no older than its oldest.");
      }
    }

    /**
     * Reads what a pack's {@code pack.mcmeta} declares.
     *
     * @param meta the pack's {@code pack.mcmeta}, at its path within the
     *             pack's archive or directory
     * @return the declared formats and description
     * @throws Archive.Rejection.Malformed when the file is not JSON, or
     *                                     declares no formats it supports
     * @throws IOException                 when the file cannot be read
     */
    public static Packmeta of(@NonNull Describe meta) throws IOException {
      JsonNode pack;
      try {
        pack = new Lease.Form.Json().readTree(meta).path("pack");
      } catch (Lease.Form.Unreadable unreadable) {
        throw new Archive.Rejection.Malformed("A pack's pack.mcmeta is not readable JSON.", unreadable);
      }
      try {
        Address root = meta.path().parent().orElse(Address.root());
        if (pack.has("min_format") && pack.has("max_format")) {
          return new Packmeta(root, Format.of(pack.get("min_format")), Format.of(pack.get("max_format")),
              pack.path("description"));
        }
        JsonNode supported = pack.path("supported_formats");
        if (supported.isObject()) {
          return new Packmeta(root, Format.of(supported.path("min_inclusive")),
              Format.of(supported.path("max_inclusive")), pack.path("description"));
        }
        if (supported.isArray()) {
          return new Packmeta(root, Format.of(supported.path(0)), Format.of(supported.path(1)),
              pack.path("description"));
        }
        Format only = Format.of(supported.isMissingNode() ? pack.path("pack_format") : supported);
        return new Packmeta(root, only, only, pack.path("description"));
      } catch (IllegalArgumentException exception) {
        throw new Archive.Rejection.Malformed("A pack's pack.mcmeta declares the formats it supports.", exception);
      }
    }

    /**
     * Finds and reads the one {@code pack.mcmeta} among the entries of a
     * pack's archive, wherever in the archive it lies. The game loads a pack
     * only when the file lies at its archive's root, so a {@link #root() root}
     * other than the root address marks a pack wrapped in a folder of its own.
     *
     * @param entries file entries of the pack's archive
     * @return the declared formats and description, and where the pack lies
     * @throws Archive.Rejection.Malformed when the archive holds no
     *                                     {@code pack.mcmeta} or several, or
     *                                     the file is malformed
     * @throws IOException                 when the file cannot be read
     */
    public static Packmeta in(@NonNull List<Archive.Entry> entries) throws IOException {
      List<Archive.Entry> metas = entries.stream()
          .filter(entry -> entry.path().fileName().equals(Optional.of(FILE)))
          .toList();
      if (metas.size() != 1) {
        throw new Archive.Rejection.Malformed("A pack declares exactly one pack.mcmeta.", null);
      }
      return of(metas.getFirst());
    }

    /**
     * Whether the pack supports a pack format.
     *
     * @param format pack format of a game version
     * @return whether it lies between the oldest and newest formats supported
     */
    public boolean supports(@NonNull Format format) {
      return oldest.compareTo(format) <= 0 && format.compareTo(newest) <= 0;
    }

    /**
     * A pack format: a major number, and a minor number that newer game
     * versions use to tell apart formats within one major.
     *
     * @param major major format number
     * @param minor minor format number, zero when none is given
     */
    public record Format(int major, int minor) implements Comparable<Format> {

      /**
       * Checks the numbers are not negative.
       *
       * @throws IllegalArgumentException when a number is negative
       */
      public Format {
        if (major < 0 || minor < 0) {
          throw new IllegalArgumentException("A pack format's numbers are not negative.");
        }
      }

      /**
       * Reads a pack format as {@code pack.mcmeta} writes it: a major number,
       * or a major and an optional minor number in an array.
       *
       * @param format the format's JSON
       * @return the format, minor zero when none is given
       * @throws IllegalArgumentException when it is neither
       */
      public static Format of(@NonNull JsonNode format) {
        if (format.isIntegralNumber()) {
          return new Format(format.asInt(), 0);
        }
        if (format.isArray() && (format.size() == 1 || format.size() == 2)
            && format.values().stream().allMatch(JsonNode::isIntegralNumber)) {
          return new Format(format.get(0).asInt(), format.size() == 2 ? format.get(1).asInt() : 0);
        }
        throw new IllegalArgumentException("A pack format is a major number, or a major and minor pair.");
      }

      @Override
      public int compareTo(@NonNull Format other) {
        return Comparator.comparingInt(Format::major)
            .thenComparingInt(Format::minor)
            .compare(this, other);
      }
    }
  }
}
