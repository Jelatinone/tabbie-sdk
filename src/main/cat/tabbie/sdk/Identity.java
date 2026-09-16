package cat.tabbie.sdk;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import lombok.NonNull;

/**
 *
 * <h2>Identity</h2>
 *
 * @param <T> the referenced domain type; this marker is checked at compile time
 * @param id  stable UUID, independent of revisions and content hashes
 *            <p>
 *            A stable UUID reference to a given resource.
 *            </p>
 *
 */
public record Identity<T>(@NonNull UUID id) {

  private static UUID identity(String canonical) {
    return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Wraps an existing UUID.
   *
   * @param <T> referenced domain type
   * @param id  existing UUID
   * @return typed identity
   */
  public static <T> Identity<T> create(@NonNull UUID id) {
    return new Identity<T>(id);
  }

  /**
   * Derives a deterministic name UUID from UTF-8 text. This is an identity,
   * not a cryptographic content digest; callers supply their own namespace.
   *
   * @param <T>       referenced domain type
   * @param canonical complete namespaced identity text
   * @return deterministic identity
   */
  public static <T> Identity<T> create(@NonNull String canonical) {
    return new Identity<T>(identity(canonical));
  }
}
