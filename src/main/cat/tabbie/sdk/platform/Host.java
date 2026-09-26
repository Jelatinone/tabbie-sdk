package cat.tabbie.sdk.platform;

import java.util.List;

import cat.tabbie.sdk.platform.Package.Manager;
import lombok.NonNull;

/**
 * A particular machine: its platform and the package managers available on
 * it, in preference order. Two hosts of one platform may have different
 * managers. The core receives each target's host from the agent running
 * there; nothing in the schema reads the local machine implicitly.
 *
 * @param platform kernel and architecture
 * @param managers available package managers, most preferred first
 */
public record Host(@NonNull Platform platform, @NonNull List<Manager> managers) {

  public Host {
    managers = List.copyOf(managers);
  }

  /**
   * Detects the machine running this JVM, once, on first use. This probes for
   * package managers, so only an agent running on the machine being installed
   * to should call it.
   *
   * @return this JVM's host
   */
  public static Host local() {
    return new Host(Platform.local(),
        Manager.ALL.stream().filter(Manager::available).toList());
  }
}