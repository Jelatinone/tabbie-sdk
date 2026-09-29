package cat.tabbie.sdk.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Response;
import lombok.NonNull;

public record Package(@NonNull String canonicalName, @NonNull Map<Manager, Specification> specifications) {

	public Package {
		specifications = Map.copyOf(specifications);

		if (canonicalName.isBlank() || canonicalName.chars().anyMatch(Character::isWhitespace)) {
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
	public Intermediate<Response> install(@NonNull Host host, @NonNull List<String> options) {
		Resolved resolved = resolve(host).orElseThrow(() -> new IllegalStateException(
				String.format("No package manager on this host carries %s.", canonicalName)));
		return resolved.manager().install(resolved.specification(), options);
	}

	/**
	 * Verifies with every manager on the host that carries this package, in the
	 * host's preference order. More than one may report it installed.
	 *
	 * @param host target machine
	 * @return verify work by manager
	 */
	public Map<Manager, Intermediate<Response>> verify(@NonNull Host host, @NonNull List<String> options) {
		Map<Manager, Intermediate<Response>> checks = new LinkedHashMap<>();
		for (Manager manager : host.managers()) {
			Specification specification = specifications.get(manager);
			if (specification != null) {
				checks.put(manager, manager.verify(specification, options));
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
	public Intermediate<Response> uninstall(@NonNull Manager manager, @NonNull List<String> options) {
		Specification specification = specifications.get(manager);
		if (specification == null) {
			throw new IllegalArgumentException(
					String.format("%s does not carry %s.", manager.name(), canonicalName));
		}
		return manager.uninstall(specification, options);
	}

	/**
	 * A package specification resolved against a compatible package manager.
	 *
	 * @param manager       selected package manager
	 * @param specification manager-specific package specification
	 */
	public record Resolved(@NonNull Manager manager, @NonNull Specification specification) {
	}

	/**
	 * A package specification, all content is manager-specific and not considered
	 * universal
	 * 
	 * @param identifier identifier of this package
	 * @param version    version of this package
	 */
	public record Specification(@NonNull String identifier, @NonNull String version) {

		public Specification {
			if (identifier.isBlank() || version.isBlank()) {
				throw new IllegalArgumentException("A specification needs a non-blank identifier and version.");
			}
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
		@NonNull
		Intermediate<Response> verify(@NonNull Specification specification, @NonNull List<String> options);

		/**
		 * 
		 * @param specification
		 * @return
		 */
		@NonNull
		Intermediate<Response> install(@NonNull Specification specification, @NonNull List<String> options);

		/**
		 * 
		 * @param specification
		 * @return
		 */
		@NonNull
		Intermediate<Response> uninstall(@NonNull Specification specification, @NonNull List<String> options);

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
			public boolean available() {
				throw new UnsupportedOperationException("Unimplemented method 'available'");
			}

			@Override
			public @NonNull Intermediate<Response> verify(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'verify'");
			}

			@Override
			public @NonNull Intermediate<Response> install(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'install'");
			}

			@Override
			public @NonNull Intermediate<Response> uninstall(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
			}

		}

		record Apt() implements Manager {

			@Override
			public String name() {
				return "apt";
			}

			@Override
			public boolean available() {
				throw new UnsupportedOperationException("Unimplemented method 'available'");
			}

			@Override
			public @NonNull Intermediate<Response> verify(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'verify'");
			}

			@Override
			public @NonNull Intermediate<Response> install(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'install'");
			}

			@Override
			public @NonNull Intermediate<Response> uninstall(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
			}

		}

		record Dnf() implements Manager {

			@Override
			public String name() {
				return "dnf";
			}

			@Override
			public boolean available() {
				throw new UnsupportedOperationException("Unimplemented method 'available'");
			}

			@Override
			public @NonNull Intermediate<Response> verify(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'verify'");
			}

			@Override
			public @NonNull Intermediate<Response> install(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'install'");
			}

			@Override
			public @NonNull Intermediate<Response> uninstall(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
			}

		}

		record Pacman() implements Manager {

			@Override
			public String name() {
				return "pacman";
			}

			@Override
			public boolean available() {
				throw new UnsupportedOperationException("Unimplemented method 'available'");
			}

			@Override
			public @NonNull Intermediate<Response> verify(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'verify'");
			}

			@Override
			public @NonNull Intermediate<Response> install(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'install'");
			}

			@Override
			public @NonNull Intermediate<Response> uninstall(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
			}

		}

		record Homebrew() implements Manager {

			@Override
			public String name() {
				return "brew";
			}

			@Override
			public boolean available() {
				throw new UnsupportedOperationException("Unimplemented method 'available'");
			}

			@Override
			public @NonNull Intermediate<Response> verify(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'verify'");
			}

			@Override
			public @NonNull Intermediate<Response> install(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'install'");
			}

			@Override
			public @NonNull Intermediate<Response> uninstall(@NonNull Specification specification,
					@NonNull List<String> options) {
				throw new UnsupportedOperationException("Unimplemented method 'uninstall'");
			}
		}

	}
}
