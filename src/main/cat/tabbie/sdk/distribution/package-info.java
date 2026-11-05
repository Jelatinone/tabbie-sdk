/**
 * Runtime families, and the files their releases carry. A {@link Distribution}
 * is a stateless family, such as the vanilla game, a mod loader, or a plugin
 * server: an enum constant a module registers beneath an edition's base,
 * {@link Java} or {@link Bedrock}, and grouped by the abstract types it
 * registers. A {@link Byproduct} is one file of a distribution's release.
 *
 * <p>
 * Installing a byproduct captures only its own content: it never downloads
 * runtimes, runs other installers, or starts processes. How it starts and
 * stops is a description, and carrying it out belongs to the consumer.
 */
package cat.tabbie.sdk.distribution;
