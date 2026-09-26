package cat.tabbie.sdk.schema;

import java.util.Locale;

import lombok.NonNull;

/**
 * An operating system kernel and processor architecture. This is a pure
 * description: it says nothing about which package managers a particular
 * machine has, so it is equal across machines of the same kind and safe as a
 * map key. See {@link Host} for a detected machine.
 *
 * @param kernel       operating system kernel
 * @param architecture processor architecture
 */
public record Platform(@NonNull Kernel kernel, @NonNull Architecture architecture) {

  /**
   * Describes the platform of the running JVM from its system properties,
   * without I/O. Only meaningful on the machine being described.
   *
   * @return platform of this JVM
   * @throws IllegalStateException when the kernel or architecture is not
   *                               recognized
   */
  public static Platform local() {
    String kernelName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String architectureName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

    Kernel kernel = switch (kernelName) {
      // startsWith: "darwin" contains "win"
      case String name when name.startsWith("windows") ->
        Kernel.WINDOWS;
      case String name when name.contains("mac") || name.contains("darwin") ->
        Kernel.MAC;
      case String name when name.contains("linux") ->
        Kernel.LINUX;
      default ->
        throw new IllegalStateException(String.format("Unrecognized kernel: %s", kernelName));
    };
    Architecture architecture = switch (architectureName) {
      case "amd64", "x86_64" -> Architecture.X_64;
      case "aarch64", "arm64" -> Architecture.ARM_64;
      default ->
        throw new IllegalStateException(String.format("Unrecognized architecture: %s", architectureName));
    };
    return new Platform(kernel, architecture);
  }

  public enum Kernel {

    WINDOWS,

    MAC,

    LINUX,
  }

  public enum Architecture {

    X_64,

    ARM_64,
  }
}