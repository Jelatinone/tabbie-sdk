/**
 * Addon catalog projects. An {@link Addon} is a provider-owned project, such
 * as a mod, plugin, or resource pack, and an {@link Addon.Build} is one
 * published release of it: a fixed set of artifacts installed together on
 * every label the build advertises. Declarations validate only their own
 * consistency; whether external dependencies are available is resolved later.
 *
 * <p>
 * The content a build carries is described in
 * {@link cat.tabbie.sdk.addon.artifact}.
 */
package cat.tabbie.sdk.addon;
