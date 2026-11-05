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
 * <li>{@link cat.tabbie.sdk.content}: content, its descriptors, and how it is
 * installed</li>
 * <li>{@link cat.tabbie.sdk.distribution}: runtime families, and the files
 * their releases carry</li>
 * <li>{@link cat.tabbie.sdk.target}: game versions, and the targets content
 * is installed on</li>
 * <li>{@link cat.tabbie.sdk.model}: the layout content is placed against, and
 * the Worlds it holds</li>
 * <li>{@link cat.tabbie.sdk.revision}: descriptions of change, and the
 * deferred work that produces them</li>
 * <li>{@link cat.tabbie.sdk.form}: the forms a file's content is read and
 * written in</li>
 * <li>{@link cat.tabbie.sdk.catalog}: providers, their projects, releases and
 * coordinates</li>
 * <li>{@link cat.tabbie.sdk.platform}: bytes, paths, packages, toolchains and
 * commands</li>
 * <li>{@link cat.tabbie.sdk.plate}: plates, their trees and leases, the
 * stash, and archives</li>
 * </ul>
 */
package cat.tabbie.sdk;
