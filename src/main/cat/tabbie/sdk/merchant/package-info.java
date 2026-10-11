/**
 * Catalog discovery and installation, shared by distribution and addon
 * providers. A {@link Provider} finds projects and their {@link Release}s and
 * mints {@link Provider.Coordinate}s that let a later process ask the same
 * provider for the same project, release, or file. An {@link Installer}
 * captures content and describes the changes that install it.
 *
 * <p>
 * Installers never write to the target installation filesystem or launch
 * processes; transport, storage, and caching belong to each provider.
 */
package cat.tabbie.sdk.merchant;
