package cat.tabbie.sdk.album.revision;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.platform.Package;
import cat.tabbie.sdk.platform.Package.Manager;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * An immutable description of a change to a file's existence, a text file's
 * content, or a package claim. Images perform no I/O and track no filesystem
 * metadata. The preimage exchanges before and after states; checking and
 * applying either description belongs to its consumer. A replacement does not
 * authorize overwriting observed content.
 *
 * @param <State> underlying existence, content, or claim state
 */
public sealed interface Image<State extends Image.Alteration> {

	/**
	 * Target this image changes
	 *
	 * @return changed file or package
	 */
	@NonNull
	Target target();

	/**
	 * Expected existence, content, or claim before the change
	 *
	 * @return expected state before
	 */
	@NonNull
	State before();

	/**
	 * Described existence, content, or claim after the change
	 *
	 * @return described state after
	 */
	@NonNull
	State after();

	/**
	 * Image with its before and after states exchanged
	 *
	 * @return image reversed
	 */
	@NonNull
	Image<State> preimage();

	/**
	 * Describes creation of a file from retained content, including zero bytes.
	 *
	 * @param path  target file
	 * @param after retained content
	 *
	 * @return resulting image
	 */
	static Image<File> create(@NonNull Relative path, @NonNull Reference.Captured after) {
		return new Installation(new File.Absent(), new File.Present(after), new Target.File(path));
	}

	/**
	 * Describes replacement of a file deleted and succeeded by another file
	 * created.
	 *
	 * @param path   target file
	 * @param before retained content before
	 * @param after  retained content after
	 *
	 * @return resulting image
	 */
	static Image<File> replace(@NonNull Relative path, @NonNull Reference.Captured before,
			@NonNull Reference.Captured after) {
		return new Installation(new File.Present(before), new File.Present(after), new Target.File(path));
	}

	/**
	 * Describes deletion of a file whose complete content matches the reference.
	 *
	 * @param path   target file
	 * @param before retained content before
	 *
	 * @return resulting image
	 */
	static Image<File> delete(@NonNull Relative path, @NonNull Reference.Captured before) {
		return new Installation(new File.Present(before), new File.Absent(), new Target.File(path));
	}

	/**
	 * Describes changes to an existing file between explicit fragment states. The
	 * fragment lists pair up into chunks.
	 *
	 * @param path   target file
	 * @param before text before
	 * @param after  text after
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Relative path, @NonNull Text before, @NonNull Text after) {
		return new Configuration(before, after, new Target.File(path));
	}

	/**
	 * Describes changes to an existing file. Each chunk contributes a fragment on
	 * each side; the chunks' insertion/deletion ordering is not retained.
	 *
	 * @param path   target file
	 * @param chunks changed chunks
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Relative path, @NonNull List<Chunk> chunks) {
		return new Configuration(chunks, new Target.File(path));
	}

	/**
	 * Describes text differences with three surrounding context lines.
	 *
	 * @param path   target file
	 * @param before text before
	 * @param after  text after
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Relative path, @NonNull String before, @NonNull String after) {
		return configure(path, Chunk.diff(before, after));
	}

	/**
	 * Retains only the differing regions and their context, not complete content
	 * expectations. Equal inputs produce empty fragment lists: the file must
	 * exist, but no particular content is described.
	 *
	 * @param path         target file
	 * @param before       text before
	 * @param after        text after
	 * @param contextLines number of context lines to keep
	 *
	 * @return resulting image
	 */
	static Image<Text> configure(@NonNull Relative path, @NonNull String before, @NonNull String after,
			int contextLines) {
		return configure(path, Chunk.diff(before, after, contextLines));
	}

	/**
	 * Describes taking a claim on a package tabbie did not claim before.
	 *
	 * @param pack package recipe to claim
	 *
	 * @return resulting image
	 */
	static Image<Claim> provision(@NonNull Package pack) {
		return new Provision(new Claim.Absent(), new Claim.Present(), new Target.Pack(pack));
	}

	/**
	 * Describes taking a claim on a package tabbie did not claim before. Each
	 * state carries the options used to reach it.
	 *
	 * @param pack    package recipe as claimed
	 * @param install options for installing, by manager
	 * @param undo    options for uninstalling again, by manager
	 *
	 * @return resulting image
	 */
	static Image<Claim> provision(@NonNull Package pack, @NonNull Map<Manager, List<String>> install,
			@NonNull Map<Manager, List<String>> undo) {
		return new Provision(new Claim.Absent(undo), new Claim.Present(install), new Target.Pack(pack));
	}

	/**
	 * Describes releasing tabbie's claim on a package claimed with this recipe.
	 *
	 * @param pack package recipe as claimed
	 *
	 * @return resulting image
	 */
	static Image<Claim> deprovision(@NonNull Package pack) {
		return new Provision(new Claim.Present(), new Claim.Absent(), new Target.Pack(pack));
	}

	/**
	 * Describes releasing tabbie's claim on a package claimed with this recipe.
	 * Each state carries the options used to reach it.
	 *
	 * @param pack      package recipe as claimed
	 * @param uninstall options for uninstalling, by manager
	 * @param undo      options for installing again, by manager
	 *
	 * @return resulting image
	 */
	static Image<Claim> deprovision(@NonNull Package pack, @NonNull Map<Manager, List<String>> uninstall,
			@NonNull Map<Manager, List<String>> undo) {
		return new Provision(new Claim.Present(undo), new Claim.Absent(uninstall), new Target.Pack(pack));
	}

	/**
	 * Checks that images can be applied in any order: every file destination and
	 * every package appears once, and no file destination is also used as a
	 * directory under the same mount.
	 *
	 * @param images generated images
	 * @throws IllegalArgumentException when generated images cannot be applied in
	 *                                  any order
	 */
	static void validate(@NonNull Collection<? extends Image<?>> images) {
		Map<Relative, Image<?>> files = new LinkedHashMap<>();
		Map<Package, Image<?>> packages = new LinkedHashMap<>();
		for (Image<?> image : images) {
			Image<?> clash = switch (image) {
				case Installation installation ->
					files.putIfAbsent(
							installation.target().relative(), installation);
				case Configuration configuration ->
					files.putIfAbsent(
							configuration.target().relative(), configuration);
				case Provision provision ->
					packages.putIfAbsent(
							provision.target().pkg(), provision);
			};
			if (clash != null) {
				throw new IllegalArgumentException("Images require unique targets: " + clash);
			}
		}
		for (Relative file : files.keySet()) {
			for (Relative parent = file.parent(); parent != null; parent = parent.parent()) {
				if (files.containsKey(parent)) {
					throw new IllegalArgumentException("A file destination may not also be used as a directory");
				}
			}
		}
	}

	/**
	 * Describes the change that undoes a set. The preimages of a valid set are
	 * valid: they change the same targets.
	 *
	 * @param images applied images
	 * @return immutable set of preimages
	 */
	static Set<Image<?>> preimage(@NonNull Collection<? extends Image<?>> images) {
		return images.stream().map(Image::preimage).collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Composes two changes of one target applied one after the other. A change
	 * followed by its exact preimage cancels. Whole files and claims compose into
	 * one change from the first's before state to the second's after state. Text
	 * changes compose through {@link Chunk#compose(List, List)}: separate regions
	 * are kept, and overlapping regions are combined where both describe the
	 * same intermediate lines. An installation and a configuration of the same
	 * file stay sequential, because folding them would mean reading the retained
	 * content.
	 *
	 * @param first  earlier change
	 * @param second later change, whose before state must agree with the
	 *               first's after state
	 * @return combined change, or empty when the pair has no net effect
	 * @throws IllegalArgumentException when targets differ or states are
	 *                                  discontinuous
	 * @throws IrreducibleException     when one file is changed by different
	 *                                  kinds of image
	 */
	static Optional<Image<?>> compose(
			@NonNull Image<?> first,
			@NonNull Image<?> second) {

		// Must be similar targets
		if (!first.target().equals(second.target())) {
			throw new IllegalArgumentException(
					"Only changes of the same target compose: %s and %s".formatted(first.target(), second.target()));
		}

		// Installation, Configuration, Provision may not be cross-composed
		if (first.getClass() != second.getClass()) {
			throw new IrreducibleException(
					"Changes of %s by different kinds of image must stay sequential".formatted(first.target()));
		}

		// Second has a discontinuity error with first
		if (!first.after().equals(second.before())) {
			throw new IllegalArgumentException(
					"Changes are discontinuous at %s".formatted(first.target()));
		}

		// Second is a preimage of first
		if (first.before().equals(second.after())) {
			return Optional.empty();
		}

		return switch (first) {
			// Collapse [P -> A -> P] or [A -> P -> A]
			case Installation earlier ->
				Optional.of(new Installation(earlier.before(), ((Installation) second).after(), earlier.target()));

			// Collapse [P -> A -> P] or [A -> P -> A]
			case Provision earlier ->
				Optional.of(new Provision(earlier.before(), ((Provision) second).after(), earlier.target()));

			// Collapse [A ⊆ B] or [B ⊆ A]
			case Configuration earlier -> {
				List<Chunk> composed = Chunk.compose(earlier.chunks(), ((Configuration) second).chunks());
				yield composed.isEmpty()
						? Optional.empty()
						: Optional.of(new Configuration(composed, earlier.target()));
			}
		};
	}

	/**
	 * Folds sequentially applied sets into the minimal set with the same net
	 * effect. Targets whose changes cancel out disappear.
	 *
	 * @apiNote Folding replaces the images a consumer applied. A consumer that
	 *          persisted install outcomes per applied provision, to decide
	 *          whether a release uninstalls, must look them up by package name
	 *          rather than by the folded image.
	 *
	 * @param changes sets in application order, each individually valid
	 * @return immutable valid net change
	 * @throws IllegalArgumentException when a set is invalid, changes are
	 *                                  discontinuous, or the net change is order
	 *                                  dependent and must stay sequential, such
	 *                                  as a file replaced by a directory of the
	 *                                  same name
	 * @throws IrreducibleException     when one file is changed by different
	 *                                  kinds of image
	 */
	static Set<Image<?>> reduce(@NonNull List<? extends Collection<? extends Image<?>>> changes) {
		Map<Target, Image<?>> net = new LinkedHashMap<>();
		for (Collection<? extends Image<?>> change : changes) {
			validate(change);
			for (Image<?> image : change) {
				Target target = image.target();
				Image<?> previous = net.remove(target);
				if (previous == null) {
					net.put(target, image);
				} else {
					compose(previous, image).ifPresent(composed -> net.put(target, composed));
				}
			}
		}
		validate(net.values());
		return Set.copyOf(net.values());
	}

	/**
	 * Describes the target of this operation
	 */
	sealed interface Target {

		/**
		 * A file, by its relative path
		 *
		 * @param relative target path beneath, never equal to, its mount
		 */
		record File(@NonNull Relative relative) implements Target {

			/**
			 * Rejects the mount itself as a file.
			 */
			public File {
				if (relative.isMount()) {
					throw new IllegalArgumentException("A file target must lie beneath its mount.");
				}
			}
		}

		/**
		 * A package, by its mapped install specifications
		 *
		 * @param pkg package
		 */
		record Pack(@NonNull Package pkg) implements Target {
		}
	}

	/**
	 * Describes the underlying state a change expects or produces: a file's
	 * existence, a text file's content, or a package claim.
	 */
	sealed interface Alteration {
	}

	/**
	 * Complete file-content expectations, distinguished from absence.
	 */
	sealed interface File extends Alteration {

		/**
		 * Assertion that no file exists at the image path.
		 */
		record Absent() implements File {
		}

		/**
		 * Assertion of complete retained file content, including zero bytes.
		 *
		 * @param reference named, verified captured content
		 */
		record Present(@NonNull Reference.Captured reference) implements File {
		}
	}

	/**
	 * Selected ordered ranges of an existing text file, rather than a
	 * complete-file snapshot. An empty fragment list asserts existence without
	 * asserting any particular content.
	 *
	 * @param fragments immutable copied ranges, possibly empty
	 */
	record Text(@NonNull List<Chunk.Fragment> fragments) implements Alteration {

		/**
		 * Copies and validates fragment order and line termination.
		 *
		 * @throws IllegalArgumentException when fragments overlap or have invalid
		 *                                  termination
		 */
		public Text {
			fragments = List.copyOf(fragments);
			Chunk.validateFragments(fragments);
		}
	}

	/**
	 * Whether tabbie holds a claim on a package. Each state carries, by manager,
	 * the options used to reach it.
	 */
	sealed interface Claim extends Alteration {

		/**
		 * No claim is held.
		 *
		 * @param uninstallOption options for releasing a claim, by manager
		 */
		record Absent(
				@NonNull Map<Package.Manager, List<String>> uninstallOption) implements Claim {

			public Absent() {
				this(Map.of());
			}

			public Absent {
				uninstallOption = copy(uninstallOption);
			}
		}

		/**
		 * A claim is held.
		 *
		 * @param installOption options for taking the claim, by manager
		 */
		record Present(
				@NonNull Map<Package.Manager, List<String>> installOption) implements Claim {

			public Present() {
				this(Map.of());
			}

			public Present {
				installOption = copy(installOption);
			}
		}

		private static Map<Package.Manager, List<String>> copy(Map<Package.Manager, List<String>> options) {
			return options.entrySet().stream()
					.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
		}
	}

	/**
	 * Creates, deletes, or replaces a whole file. Both sides may be present, but
	 * both sides may not be absent. Equal references describe an unchanged file.
	 *
	 * @param before complete content or absence expected before the change
	 * @param after  complete content or absence described after the change
	 * @param target target file
	 */
	record Installation(
			@NonNull File before, @NonNull File after, @NonNull Target.File target)
			implements Image<Image.File> {

		/**
		 * Rejects an absence-to-absence description.
		 *
		 * @throws IllegalArgumentException when both sides are absent
		 */
		public Installation {
			if (before instanceof File.Absent && after instanceof File.Absent) {
				throw new IllegalArgumentException("Files may not both be absent!");
			}
		}

		@Override
		public Installation preimage() {
			return new Installation(after, before, target);
		}
	}

	/**
	 * Changes selected ranges of an existing text file, stored as corresponding
	 * chunks. A configuration never creates or deletes a file; an installation
	 * does.
	 *
	 * @param chunks text changes
	 * @param target target file
	 */
	record Configuration(
			@NonNull List<Chunk> chunks, @NonNull Target.File target)
			implements Image<Image.Text> {

		/**
		 * Copies and validates the chunks.
		 */
		public Configuration {
			chunks = List.copyOf(chunks);
			Chunk.validateChunks(chunks);
		}

		/**
		 * Describes the change between two fragment states of an existing file.
		 *
		 * @param before text before
		 * @param after  text after
		 * @param target target file
		 */
		public Configuration(
				@NonNull Text before, @NonNull Text after, @NonNull Target.File target) {

			this(Chunk.between(before.fragments(), after.fragments()), target);
		}

		@Override
		public Text before() {
			return new Text(
					chunks.stream()
							.map(Chunk::before)
							.toList());
		}

		@Override
		public Text after() {
			return new Text(
					chunks.stream()
							.map(Chunk::after)
							.toList());
		}

		@Override
		public Configuration preimage() {
			return new Configuration(
					chunks.stream()
							.map(Chunk::invert)
							.toList(),
					target);
		}
	}

	/**
	 * Takes or releases a package claim. Both sides may not be absent.
	 *
	 * @param before claim expected before the change
	 * @param after  claim described after the change
	 * @param target claimed package
	 */
	record Provision(@NonNull Claim before, @NonNull Claim after, @NonNull Target.Pack target)
			implements Image<Image.Claim> {

		/**
		 * Rejects an absence-to-absence description.
		 */
		public Provision {
			if (before instanceof Claim.Absent && after instanceof Claim.Absent) {
				throw new IllegalArgumentException("A provision changes at least one package");
			}

			Map<Package.Manager, List<String>> uninstall = switch (before) {
				case Claim.Absent(var paths) -> paths;
				case Claim.Present(var paths) -> paths;
			};
			Map<Package.Manager, List<String>> install = switch (after) {
				case Claim.Absent(var paths) -> paths;
				case Claim.Present(var paths) -> paths;
			};

			if (!uninstall.isEmpty() && !install.isEmpty()
					&& Collections.disjoint(uninstall.keySet(), install.keySet())) {
				throw new IllegalArgumentException("There must be at least one valid install-uninstall path");
			}
		}

		@Override
		public @NonNull Image<Claim> preimage() {
			return new Provision(after, before, target);
		}
	}

	/**
	 * Signals that changes of one target cannot be folded without observing the
	 * complete content, for example an installation followed by a configuration
	 * of the same file. The caller keeps the changes sequential or observes the
	 * whole file instead.
	 */
	final class IrreducibleException extends IllegalStateException {

		/**
		 * Serialization version.
		 */
		@Serial
		private static final long serialVersionUID = 1L;

		/**
		 * Reports changes that cannot be folded.
		 *
		 * @param message description naming the target
		 */
		public IrreducibleException(String message) {
			super(message);
		}

		/**
		 * Reports changes that cannot be folded, with the failure that revealed it.
		 *
		 * @param message   description naming the target
		 * @param throwable underlying failure
		 */
		public IrreducibleException(String message, Throwable throwable) {
			super(message, throwable);
		}
	}
}