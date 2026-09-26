package cat.tabbie.sdk.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Response;
import lombok.NonNull;

public record Package(@NonNull String name, @NonNull Map<Manager, Specification> specifications) {

  public Package {
    specifications = Map.copyOf(specifications);

    if (name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException("A package needs a non-blank name without whitespace.");
    }
    if (specifications.isEmpty()) {
      throw new IllegalArgumentException("A package needs at least one specification.");
    }
  }

  /**
   * Selects the host's most preferred manager that carries this package.
   *
   * @param host target machine
   * @return selected manager and its specification, empty when none carries it
   */
  public Optional<Resolved> resolve(@NonNull Host host) {
    return host.managers().stream()
        .filter(specifications::containsKey)
        .findFirst()
        .map(manager -> new Resolved(manager, specifications.get(manager)));
  }

  /**
   * Installs through the host's most preferred manager that carries this
   * package.
   *
   * @param host target machine
   * @return install work; see {@link Manager#install(Specification)}
   * @throws IllegalStateException when no manager on the host carries it
   */
  public Intermediate<Response> install(@NonNull Host host) {
    Resolved resolved = resolve(host).orElseThrow(() -> new IllegalStateException(
        String.format("No package manager on this host carries %s.", name)));
    return resolved.manager().install(resolved.specification());
  }

  /**
   * Verifies with every manager on the host that carries this package, in the
   * host's preference order. More than one may report it installed.
   *
   * @param host target machine
   * @return verify work by manager
   */
  public Map<Manager, Intermediate<Response>> verify(@NonNull Host host) {
    Map<Manager, Intermediate<Response>> checks = new LinkedHashMap<>();
    for (Manager manager : host.managers()) {
      Specification specification = specifications.get(manager);
      if (specification != null) {
        checks.put(manager, manager.verify(specification));
      }
    }
    return Collections.unmodifiableMap(checks);
  }

  /**
   * Uninstalls through the manager that installed this package. Call it on the
   * package held by a release's before state: that is the recipe as it was
   * claimed, so the specification matches what was actually installed.
   *
   * @param manager manager that installed the package
   * @return uninstall work; see {@link Manager#uninstall(Specification)}
   * @throws IllegalArgumentException when the manager does not carry it
   */
  public Intermediate<Response> uninstall(@NonNull Manager manager) {
    Specification specification = specifications.get(manager);
    if (specification == null) {
      throw new IllegalArgumentException(
          String.format("%s does not carry %s.", manager.name(), name));
    }
    return manager.uninstall(specification);
  }

  /**
   * A package specification resolved against a compatible package manager.
   *
   * @param manager       selected package manager
   * @param specification manager-specific package specification
   */
  public record Resolved(
      @NonNull Manager manager,
      @NonNull Specification specification) {
  }

  public record Specification(@NonNull String identifier, @NonNull String version, @NonNull List<String> options) {

    public Specification {
      options = List.copyOf(options);
      if (identifier.isBlank() || version.isBlank()) {
        throw new IllegalArgumentException("A specification needs a non-blank identifier and version.");
      }
    }

    public Specification(@NonNull String identifier, @NonNull String version) {
      this(identifier, version, List.of());
    }

  }

  public sealed interface Manager {

    /**
     * Windows Package Manager
     */
    Manager WINGET = new Winget();

    /**
     * Debian and Ubuntu APT
     */
    Manager APT = new Apt();

    /**
     * Fedora and RHEL DNF
     */
    Manager DNF = new Dnf();

    /**
     * Arch Linux pacman
     */
    Manager PACMAN = new Pacman();

    /**
     * Homebrew
     */
    Manager HOMEBREW = new Homebrew();

    /**
     * All compatible package managers
     */
    List<Manager> ALL = List.of(WINGET, APT, DNF, PACMAN, HOMEBREW);

    /**
     * 
     * @return
     */
    String name();

    /**
     * 
     * @param specification
     * @return
     */
    Intermediate<Response> verify(Specification specification);

    /**
     * 
     * @param specification
     * @return
     */
    Intermediate<Response> install(Specification specification);

    /**
     * 
     * @param specification
     * @return
     */
    Intermediate<Response> uninstall(Specification specification);

    /**
     * Determine whether this manager is available on this platform
     * 
     * @apiNote Should never make reference to <code>Platform.CURRENT</code>, which
     *          depends on this method to determine available managers.
     * 
     * @return
     */
    boolean available();

    record Winget() implements Manager {

      @Override
      public String name() {
        return "winget";
      }

      @Override
      public Intermediate<Response> verify(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'verify'");
      }

      @Override
      public Intermediate<Response> install(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'install'");
      }

      @Override
      public Intermediate<Response> uninstall(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
      }

      @Override
      public boolean available() {
        throw new UnsupportedOperationException("Unimplemented method 'available'");
      }

    }

    record Apt() implements Manager {

      @Override
      public String name() {
        return "apt";
      }

      @Override
      public Intermediate<Response> verify(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'verify'");
      }

      @Override
      public Intermediate<Response> install(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'install'");
      }

      @Override
      public Intermediate<Response> uninstall(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
      }

      @Override
      public boolean available() {
        throw new UnsupportedOperationException("Unimplemented method 'available'");
      }

    }

    record Dnf() implements Manager {

      @Override
      public String name() {
        return "dnf";
      }

      @Override
      public Intermediate<Response> verify(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'verify'");
      }

      @Override
      public Intermediate<Response> install(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'install'");
      }

      @Override
      public Intermediate<Response> uninstall(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
      }

      @Override
      public boolean available() {
        throw new UnsupportedOperationException("Unimplemented method 'available'");
      }

    }

    record Pacman() implements Manager {

      @Override
      public String name() {
        return "pacman";
      }

      @Override
      public Intermediate<Response> verify(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'verify'");
      }

      @Override
      public Intermediate<Response> install(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'install'");
      }

      @Override
      public Intermediate<Response> uninstall(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
      }

      @Override
      public boolean available() {
        throw new UnsupportedOperationException("Unimplemented method 'available'");
      }

    }

    record Homebrew() implements Manager {

      @Override
      public String name() {
        return "brew";
      }

      @Override
      public Intermediate<Response> verify(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'verify'");
      }

      @Override
      public Intermediate<Response> install(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'install'");
      }

      @Override
      public Intermediate<Response> uninstall(Specification specification) {
        throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
      }

      @Override
      public boolean available() {
        throw new UnsupportedOperationException("Unimplemented method 'available'");
      }
    }

  }
}
