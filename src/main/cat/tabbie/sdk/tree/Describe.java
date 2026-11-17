package cat.tabbie.sdk.tree;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * What is known about a file's bytes without reading them: the name they are
 * known by, their size, and the digests known so far, bound to the file whose
 * bytes they are. Describing never reads bytes; reading them is the file's
 * {@link Shape.File#read() lease}. A description may know few digests, such
 * as an archive entry's CRC-32 before its bytes are kept, and more once they
 * are.
 *
 * <p>
 * Two descriptions are equal when their sizes and digests are, whatever their
 * names or the files they are bound to, so the same bytes described in two
 * places are equal. Whether two descriptions that know different digests can
 * be of the same bytes is {@link #agrees(Describe)}.
 *
 * @param name    portable filename the bytes are known by
 * @param size    size of the bytes
 * @param digests digests known so far, each in lowercase hexadecimal
 * @param shape   the file whose bytes these are
 */
public record Describe(
    @NonNull String name,
    long size,
    @NonNull Map<Digest, String> digests,
    @NonNull Shape.File shape) {

  /**
   * Copies the digests, and checks the name, the size and each digest's text.
   *
   * @throws IllegalArgumentException when the name is not a portable single
   *                                  filename, the size is negative, or a
   *                                  digest is not lowercase hexadecimal of its
   *                                  algorithm's length
   */
  public Describe {
    if (!Address.portable(name)) {
      throw new IllegalArgumentException(String.format("\"%s\" is not a portable single filename.", name));
    }
    digests = Map.copyOf(digests);
    if (size < 0) {
      throw new IllegalArgumentException("A file's size is never negative.");
    }
    if (digests.entrySet().stream().anyMatch(digest -> !digest.getKey().matches(digest.getValue()))) {
      throw new IllegalArgumentException("A digest is lowercase hexadecimal of its algorithm's length.");
    }
  }

  /**
   * Describes bytes held in memory, knowing every digest of them.
   *
   * @param name  portable filename the bytes are known by
   * @param bytes the bytes, which are not kept
   * @param shape the file whose bytes these are
   * @return the description
   * @throws IllegalArgumentException when the name is not a portable single
   *                                  filename
   */
  public static Describe of(@NonNull String name, @NonNull byte[] bytes, @NonNull Shape.File shape) {
    Map<Digest, String> digests = new EnumMap<>(Digest.class);
    for (Digest digest : Digest.values()) {
      digests.put(digest, digest.compute(bytes));
    }
    return new Describe(name, bytes.length, digests, shape);
  }

  /**
   * Returns one digest, when it is known.
   *
   * @param digest the digest's algorithm
   * @return lowercase hexadecimal digest, or empty while it is not known
   */
  public Optional<String> digest(@NonNull Digest digest) {
    return Optional.ofNullable(digests.get(digest));
  }

  /**
   * Whether this and another description can be of the same bytes: their
   * sizes are equal, and no digest both know differs.
   *
   * @param other the other description
   * @return true when nothing known tells the bytes apart
   */
  public boolean agrees(@NonNull Describe other) {
    return size == other.size()
        && digests.entrySet().stream()
            .allMatch(known -> other.digest(known.getKey()).map(known.getValue()::equals).orElse(true));
  }

  /**
   * Binds this description to another file holding the same bytes, such as
   * where core keeps them.
   *
   * @param shape the file the bytes are now read from
   * @return the description, bound to that file
   */
  public Describe withShape(@NonNull Shape.File shape) {
    return new Describe(name, size, digests, shape);
  }

  /**
   * Compares the size and digests alone.
   *
   * @param other object to compare
   * @return whether the other is a description of equal size and digests
   */
  @Override
  public boolean equals(Object other) {
    return other instanceof Describe describe && size == describe.size() && digests.equals(describe.digests());
  }

  /**
   * Hashes the size and digests alone, as {@link #equals(Object)} compares.
   *
   * @return hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(size, digests);
  }

  /**
   * An algorithm digesting a file's bytes, and the length of its lowercase
   * hexadecimal text.
   */
  @Getter
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
  public enum Digest {

    /**
     * CRC-32, as archives declare for their entries.
     */
    CRC32("CRC-32", 8) {
      @Override
      public String compute(@NonNull byte[] bytes) {
        java.util.zip.CRC32 checksum = new java.util.zip.CRC32();
        checksum.update(bytes);
        return String.format("%08x", checksum.getValue());
      }
    },

    /**
     * SHA-1, as some providers declare.
     */
    SHA1("SHA-1", 40),

    /**
     * SHA-256, the digest tabbie keeps bytes by.
     */
    SHA256("SHA-256", 64),

    /**
     * SHA-512, as some providers declare.
     */
    SHA512("SHA-512", 128);

    /**
     * The algorithm's standard name.
     *
     * @return the algorithm
     */
    String algorithm;

    /**
     * How many hexadecimal digits the digest's text has.
     *
     * @return the text's length
     */
    int length;

    /**
     * Whether text is this digest in lowercase hexadecimal.
     *
     * @param value text to check
     * @return true when it has this digest's length and only lowercase
     *         hexadecimal digits
     */
    public boolean matches(@NonNull String value) {
      return value.length() == length
          && value.chars().allMatch(character -> Character.isDigit(character) || character >= 'a' && character <= 'f');
    }

    /**
     * Computes this digest of bytes held in memory.
     *
     * @param bytes the bytes
     * @return lowercase hexadecimal digest
     */
    public String compute(@NonNull byte[] bytes) {
      try {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
      } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException(String.format("The Java runtime must provide %s.", algorithm), exception);
      }
    }
  }
}
