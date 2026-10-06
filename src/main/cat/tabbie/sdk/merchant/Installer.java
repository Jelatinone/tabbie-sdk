package cat.tabbie.sdk.merchant;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.revision.Image;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * Describes content without applying it. Installers capture bytes into the
 * caller's retention backend and describe changes; they never write to the
 * target installation filesystem or launch processes.
 *
 * @param <Contextual> accepted target context
 */
@FunctionalInterface
public interface Installer<Contextual extends Installer.Context> {

	/**
	 * Captures and describes the supplied content.
	 *
	 * @param context target context
	 * @return unevaluated capture producing validated images
	 * @throws IOException when layout resolution fails
	 */
	Intermediate<Set<Image<?>>> install(@NonNull Contextual context) throws IOException;

	/**
	 * Selected target, logical mounts, and caller-owned retention, independent of
	 * physical paths.
	 */
	interface Context {

		/**
		 * Returns the explicitly selected target.
		 *
		 * @return selected target
		 */
		@NonNull
		Label label();

		/**
		 * Returns the platform of the machine being installed to, for selecting
		 * native content.
		 *
		 * @return target platform
		 */
		@NonNull
		Platform platform();

		/**
		 * Returns the caller-owned retention backend receiving captures.
		 *
		 * @return caller-owned retention backend
		 */
		@NonNull
		Extract repository();

		/**
		 * Returns the context mount relative to the installation's working
		 * directory, the mount itself for that directory.
		 *
		 * @return context mount
		 */
		@NonNull
		Relative.Root contextRoot();

		/**
		 * Returns the selected world's directory, as components beneath the context
		 * mount. World-scoped paths resolve beneath it. Every context has a world
		 * mount; callers that have not selected a world must reject world-scoped
		 * layouts themselves rather than bind an arbitrary directory.
		 *
		 * @return world directory relative to the context mount, empty when the
		 *         world is the context mount itself
		 */
		@NonNull
		Relative.World worldRoot();

		/**
		 * Resolves a scoped logical path to a path relative to the installation's
		 * working directory, using this context's mounts. Core uses this resolver
		 * for every generated image.
		 *
		 * @param relative context- or world-scoped path
		 * @return installation-relative path
		 */
		default Relative.Root resolve(@NonNull Relative relative) {
			return switch (relative) {
				case Relative.Root root -> contextRoot()
						.resolve(root.components());
				case Relative.World world -> contextRoot()
						.resolve(worldRoot().components())
						.resolve(world.components());
			};
		}
	}
}