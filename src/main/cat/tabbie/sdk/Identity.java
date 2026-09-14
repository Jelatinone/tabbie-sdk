package cat.tabbie.sdk;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import lombok.NonNull;

/**
 *
 * <h1>Identity</h1>
 *
 * <p>
 * A stable UUID reference to a given resource.
 * </p>
 *
 */
public record Identity<T>(UUID id) {

  private static UUID identity(String canonical) {
    return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
  }

  public static <T> Identity<T> create(@NonNull UUID id) {
    return new Identity<T>(id);
  }

  public static <T> Identity<T> create(@NonNull String canonical) {
    return new Identity<T>(identity(canonical));
  }
}
