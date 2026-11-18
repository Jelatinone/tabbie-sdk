/**
 * Runtime families, and the files their releases carry. A {@link Distribution}
 * is a stateless family, such as the vanilla game, a mod loader, or a plugin
 * server: an enum constant a module registers beneath an edition's base,
 * {@link Java} or {@link Bedrock}, and grouped by the abstract types it
 * registers. A {@link Distributable} is one file of a distribution's release,
 * placed where the {@link cat.tabbie.sdk.meta.action.Place place} for its type
 * says.
 *
 * <p>
 * Installing a distributable captures only its own content: it never downloads
 * runtimes, runs other installers, or starts processes. How a Cat starts,
 * stops and holds its saving is contributed for its distribution by the
 * {@link cat.tabbie.sdk.meta.action.Start Start},
 * {@link cat.tabbie.sdk.meta.action.Stop Stop},
 * {@link cat.tabbie.sdk.meta.action.Restart Restart},
 * {@link cat.tabbie.sdk.meta.action.Hold Hold} and
 * {@link cat.tabbie.sdk.meta.action.Resume Resume} actions, as descriptions;
 * carrying them out belongs to core.
 */
package cat.tabbie.sdk.distribution;
