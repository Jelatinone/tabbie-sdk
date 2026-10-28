/**
 * Runtime families and their releases. A {@link Distribution} is a stateless
 * family, such as the native game, a mod loader, or a plugin server, whose
 * public values are the enum constants nested in {@link Java} and
 * {@link Bedrock}. A {@link Product} is a provider-owned project publishing
 * releases of one distribution, and each {@link Byproduct} is one file of such
 * a release. Each release declares the layout of a Cat running it, which says
 * where its content goes.
 *
 * <p>
 * Installing a byproduct captures only its own content: it never downloads
 * runtimes, runs other installers, or starts processes. How it starts and
 * stops is a description, and carrying it out belongs to the consumer.
 */
package cat.tabbie.sdk.minecraft.distribution;
