package cat.tabbie.sdk.platform;

import java.util.List;

import cat.tabbie.sdk.platform.Package.Manager;
import lombok.NonNull;

/**
 * A particular machine: its platform and the package managers available on
 * it, in preference order. Two hosts of one platform may have different
 * managers. Executors detect the managers on their own machine by running each
 * manager's {@link Manager#probe()}. Within the SDK, only scrutinizing a
 * provision inspects a machine, probing the managers its recipe names.
 *
 * @param platform kernel and architecture
 * @param managers available package managers, most preferred first, without
 *                 repeats
 */
public record Host(@NonNull Platform platform, @NonNull List<Manager> managers) {

  /**
   * Copies the managers.
   *
   * @throws IllegalArgumentException when a manager repeats
   */
  public Host {
    managers = List.copyOf(managers);
    if (managers.stream().distinct().count() != managers.size()) {
      throw new IllegalArgumentException("A host lists each package manager once.");
    }
  }
}
