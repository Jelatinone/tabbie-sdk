package cat.tabbie.sdk.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * A system package recipe: one logical package, such as a Java runtime,
 * spelled for each package manager that carries it. Recipes describe commands;
 * they never run them. An executor on the target machine runs the commands,
 * elevating them when {@link Manager#privileged()} requires it.
 *
 * @param canonicalName  stable name without whitespace, shared by every
 *                       manager's spelling
 * @param specifications manager-specific spellings, at least one
 */
public record Package(@NonNull String canonicalName, @NonNull Map<Manager, Specification> specifications) {

	/**
	 * Copies the specifications and checks the name.
	 *
	 * @throws IllegalArgumentException when the name is blank or contains
	 *                                  whitespace, or no manager carries the
	 *                                  package
	 */
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
	 * Describes installation through the host's most preferred manager that
	 * carries this package.
	 *
	 * @param host    target machine
	 * @param options extra manager options
	 * @return install command; see {@link Manager#install(Specification, List)}
	 * @throws IllegalStateException when no manager on the host carries it
	 */
	public Command install(@NonNull Host host, @NonNull List<String> options) {
		Resolved resolved = resolve(host).orElseThrow(() -> new IllegalStateException(
				String.format("No package manager on this host carries %s.", canonicalName)));
		return resolved.manager().install(resolved.specification(), options);
	}

	/**
	 * Describes verification with every manager on the host that carries this
	 * package, in the host's preference order. More than one may report it
	 * installed.
	 *
	 * @param host target machine
	 * @return verify commands by manager; see {@link Manager#verify(Specification)}
	 */
	public Map<Manager, Command> verify(@NonNull Host host) {
		Map<Manager, Command> checks = new LinkedHashMap<>();
		for (Manager manager : host.managers()) {
			Specification specification = specifications.get(manager);
			if (specification != null) {
				checks.put(manager, manager.verify(specification));
			}
		}
		return Collections.unmodifiableMap(checks);
	}

	/**
	 * Describes removal through the manager that installed this package. Call it
	 * on the recipe held by a claim's before state: that is the recipe as it was
	 * claimed, so the specification matches what was actually installed.
	 *
	 * @param manager manager that installed the package
	 * @param options extra manager options
	 * @return uninstall command; see {@link Manager#uninstall(Specification, List)}
	 * @throws IllegalArgumentException when the manager does not carry it
	 */
	public Command uninstall(@NonNull Manager manager, @NonNull List<String> options) {
		Specification specification = specifications.get(manager);
		if (specification == null) {
			throw new IllegalArgumentException(
					String.format("%s does not carry %s.", manager.id(), canonicalName));
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
	 * A manager-specific package spelling. Identifiers and versions use the
	 * manager's own syntax and are not comparable across managers.
	 *
	 * @param identifier manager package identifier
	 * @param version    exact manager version to install, or empty for the
	 *                   version the manager currently offers
	 */
	public record Specification(@NonNull String identifier, @NonNull Optional<String> version) {

		/**
		 * Rejects blank or whitespace-containing values.
		 *
		 * @throws IllegalArgumentException when the identifier or a present version
		 *                                  is blank or contains whitespace
		 */
		public Specification {
			if (identifier.isBlank() || identifier.chars().anyMatch(Character::isWhitespace)
					|| version.filter(value -> value.isBlank() || value.chars().anyMatch(Character::isWhitespace))
							.isPresent()) {
				throw new IllegalArgumentException("A specification needs a non-blank identifier and version.");
			}
		}

		/**
		 * Specifies whatever version the manager currently offers.
		 *
		 * @param identifier manager package identifier
		 */
		public Specification(@NonNull String identifier) {
			this(identifier, Optional.empty());
		}

		/**
		 * Specifies an exact manager version.
		 *
		 * @param identifier manager package identifier
		 * @param version    exact manager version
		 */
		public Specification(@NonNull String identifier, @NonNull String version) {
			this(identifier, Optional.of(version));
		}
	}

	/**
	 * A system package manager, described as the commands it is driven with.
	 * Executors run these commands without a shell, non-interactively, and judge
	 * success by exit status.
	 */
	@AllArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	public enum Manager {

		/**
		 * Windows Package Manager, installing exact package identifiers silently.
		 */
		WINGET("winget", "winget", false) {

			@Override
			public Command verify(@NonNull Specification specification) {
				return Command.of("winget", "list", "--id", specification.identifier(), "--exact",
						"--accept-source-agreements");
			}

			@Override
			public Command install(@NonNull Specification specification, @NonNull List<String> options) {
				List<String> trailing = new ArrayList<>(List.of("--id", specification.identifier(), "--exact"));
				specification.version().ifPresent(version -> trailing.addAll(List.of("--version", version)));
				return Command.of(program(), List.of("install", "--silent", "--disable-interactivity",
						"--accept-package-agreements", "--accept-source-agreements"), options, trailing);
			}

			@Override
			public Command uninstall(@NonNull Specification specification, @NonNull List<String> options) {
				return Command.of(program(), List.of("uninstall", "--silent", "--disable-interactivity"), options,
						List.of("--id", specification.identifier(), "--exact"));
			}
		},

		/**
		 * Debian and Ubuntu APT, pinning versions as {@code package=version}.
		 */
		APT("apt", "apt-get", true) {

			@Override
			public Command verify(@NonNull Specification specification) {
				return Command.of("dpkg-query", "--show", specification.identifier());
			}

			@Override
			public Command install(@NonNull Specification specification, @NonNull List<String> options) {
				String target = specification.version()
						.map(version -> specification.identifier() + "=" + version)
						.orElse(specification.identifier());
				return Command.of(program(), List.of("install", "--yes", "--quiet"), options, List.of(target));
			}

			@Override
			public Command uninstall(@NonNull Specification specification, @NonNull List<String> options) {
				return Command.of(program(), List.of("remove", "--yes", "--quiet"), options,
						List.of(specification.identifier()));
			}
		},

		/**
		 * Fedora and RHEL DNF, pinning versions as {@code package-version}.
		 */
		DNF("dnf", "dnf", true) {

			@Override
			public Command verify(@NonNull Specification specification) {
				return Command.of("rpm", "--query", specification.identifier());
			}

			@Override
			public Command install(@NonNull Specification specification, @NonNull List<String> options) {
				String target = specification.version()
						.map(version -> specification.identifier() + "-" + version)
						.orElse(specification.identifier());
				return Command.of(program(), List.of("install", "--assumeyes", "--quiet"), options, List.of(target));
			}

			@Override
			public Command uninstall(@NonNull Specification specification, @NonNull List<String> options) {
				return Command.of(
						program(), List.of("remove", "--assumeyes", "--quiet"), options,
						List.of(specification.identifier()));
			}
		},

		/**
		 * Arch Linux pacman. Repositories carry one version, so versions are not
		 * pinned.
		 */
		PACMAN("pacman", "pacman", true) {

			@Override
			public Command verify(@NonNull Specification specification) {
				return Command.of(program(), "--query", specification.identifier());
			}

			@Override
			public Command install(@NonNull Specification specification, @NonNull List<String> options) {
				return Command.of(
						program(), List.of("--sync", "--needed", "--noconfirm"), options,
						List.of(specification.identifier()));
			}

			@Override
			public Command uninstall(@NonNull Specification specification, @NonNull List<String> options) {
				return Command.of(program(), List.of("--remove", "--noconfirm"), options, List.of(specification.identifier()));
			}
		},

		/**
		 * Homebrew, which refuses to run as root. Versioned formulae are separate
		 * identifiers, such as {@code openjdk@21}, so versions are not pinned.
		 */
		HOMEBREW("brew", "brew", false) {

			@Override
			public Command verify(@NonNull Specification specification) {
				return Command.of(program(), "list", "--versions", specification.identifier());
			}

			@Override
			public Command install(@NonNull Specification specification, @NonNull List<String> options) {
				return Command.of(program(), List.of("install", "--quiet"), options, List.of(specification.identifier()));
			}

			@Override
			public Command uninstall(@NonNull Specification specification, @NonNull List<String> options) {
				return Command.of(program(), List.of("uninstall", "--quiet"), options, List.of(specification.identifier()));
			}
		};

		@NonNull
		String id;
		@NonNull
		String program;
		boolean privileged;

		/**
		 * Looks up a manager by its {@link #id()}, for example when reading a
		 * persisted claim.
		 *
		 * @param id manager name such as {@code apt}
		 * @return matching manager, or empty when unknown
		 */
		public static Optional<Manager> of(@NonNull String id) {
			return Arrays.stream(values()).filter(manager -> manager.id.equals(id)).findFirst();
		}

		/**
		 * Returns the stable manager name. Unlike {@link #name()}, it does not
		 * change when a constant is renamed.
		 *
		 * @return name such as {@code dpkg-query}
		 */
		public String id() {
			return id;
		}

		/**
		 * Returns the common program executable name, separate from {@link #id()}, it
		 * may or may not differ depending on usage
		 * 
		 * @return name such as {@code rpf}
		 */
		public String program() {
			return program;
		}

		/**
		 * Whether install and uninstall commands need administrative rights. The
		 * executor decides how to elevate; commands never include {@code sudo}.
		 *
		 * @return whether elevation is required
		 */
		public boolean privileged() {
			return privileged;
		}

		/**
		 * Describes a cheap check that this manager is usable on a machine. Exit
		 * status 0 means available; failure to start the program means absent.
		 *
		 * @return availability command
		 */
		public Command probe() {
			return Command.of(program, "--version");
		}

		/**
		 * Describes a check that the package is installed. Exit status 0 means the
		 * manager reports it installed, in any version.
		 *
		 * @param specification package spelling
		 * @return verification command
		 */
		public abstract Command verify(@NonNull Specification specification);

		/**
		 * Describes non-interactive installation. Exit status 0 means installed.
		 * Managers that cannot pin versions install their current version.
		 *
		 * @param specification package spelling
		 * @param options       extra manager options, placed before the package
		 * @return install command
		 */
		public abstract Command install(@NonNull Specification specification, @NonNull List<String> options);

		/**
		 * Describes non-interactive removal. Exit status 0 means removed.
		 *
		 * @param specification package spelling
		 * @param options       extra manager options, placed before the package
		 * @return uninstall command
		 */
		public abstract Command uninstall(@NonNull Specification specification, @NonNull List<String> options);
	}
}