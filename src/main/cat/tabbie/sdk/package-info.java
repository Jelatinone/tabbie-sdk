/**
 * Tabbie's software development kit: the contracts core, its executors, its
 * command line, and every extension share. The {@link Registry} reads every
 * module's registration into the distributions, content families and
 * contributions core chooses between.
 *
 * <p>
 * The subpackages divide the SDK by concern:
 * <ul>
 * <li>{@link cat.tabbie.sdk.meta.action}, {@link cat.tabbie.sdk.meta.attribute}
 * and {@link cat.tabbie.sdk.meta.registry}: the annotations a module declares
 * what it contributes with, and the registration it provides</li>
 * <li>{@link cat.tabbie.sdk.content}: content, and how it is installed</li>
 * <li>{@link cat.tabbie.sdk.distribution}: runtime families, and the files
 * their releases carry</li>
 * <li>{@link cat.tabbie.sdk.target}: game versions, and the targets content
 * is installed on</li>
 * <li>{@link cat.tabbie.sdk.tree}: shapes and their leases, addresses,
 * namespaces, and the failures of describing and carrying out changes</li>
 * <li>{@link cat.tabbie.sdk.model}: the layout content is placed
 * against</li>
 * <li>{@link cat.tabbie.sdk.revision}: descriptions of change, the deferred
 * work that carries them out, and the plates it runs on</li>
 * <li>{@link cat.tabbie.sdk.form}: shapes read in a format</li>
 * <li>{@link cat.tabbie.sdk.catalog}: providers, their projects, releases and
 * coordinates</li>
 * <li>{@link cat.tabbie.sdk.platform}: machines, packages, toolchains,
 * commands, and the observer</li>
 * </ul>
 *
 * <p>
 * This package is exported to core alone: an extension describes content and
 * the images placing it, and only core holds the registry.
 */
package cat.tabbie.sdk;
