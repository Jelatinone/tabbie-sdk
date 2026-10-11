/**
 * Runtime families and their releases. A {@link Distribution} is a stateless
 * family, such as the native game, a mod loader, or a plugin server, whose
 * public values are the enum constants nested in {@link Java} and
 * {@link Bedrock}. A {@link Product} is a provider-owned project publishing
 * releases of one distribution, and each {@link Byproduct} is one file of such
 * a release.
 *
 * <p>
 * Describing a byproduct never downloads runtimes, runs installers, or starts
 * processes; carrying out its descriptions belongs to the consumer.
 */
package cat.tabbie.sdk.minecraft.distribution;
