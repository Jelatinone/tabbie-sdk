/**
 * Shared foundations of the SDK. {@link Identity} is the stable UUID reference
 * that the subpackages use to name providers and catalog items independently
 * of revisions and content hashes.
 *
 * <p>
 * The subpackages divide the SDK by concern:
 * <ul>
 * <li>{@link cat.tabbie.sdk.minecraft} and
 * {@link cat.tabbie.sdk.minecraft.distribution}: game versions, runtime
 * families, and the targets content is installed on</li>
 * <li>{@link cat.tabbie.sdk.merchant}: catalog discovery, releases, and
 * installers</li>
 * <li>{@link cat.tabbie.sdk.addon} and {@link cat.tabbie.sdk.addon.artifact}:
 * addon projects, their builds, and the content those builds carry</li>
 * <li>{@link cat.tabbie.sdk.album.repository}: describing, retaining, and
 * unpacking content bytes</li>
 * <li>{@link cat.tabbie.sdk.album.revision}: descriptions of changes and the
 * deferred work that produces them</li>
 * <li>{@link cat.tabbie.sdk.platform}: logical paths, machines, package
 * managers, and commands</li>
 * <li>{@link cat.tabbie.sdk.api}: provider-agnostic queries and progress
 * callbacks</li>
 * </ul>
 */
package cat.tabbie.sdk;
