/**
 * Catalog discovery and installation, shared by distribution and addon
 * providers. A {@link Provider} finds projects and their {@link Release}s and
 * mints {@link Provider.Coordinate}s that let a later process ask the same
 * provider for the same project, release, or file. An {@link Installer} runs
 * on the plate its context names: it captures content into that plate's stash
 * and describes the images that place it, beneath the directory of the plate's
 * tree that accepts it.
 *
 * <p>
 * Installers never write to a Cat's files or launch processes; transport,
 * storage, and caching belong to each provider.
 */
package cat.tabbie.sdk.merchant;
