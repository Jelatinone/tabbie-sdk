/**
 * Minecraft targets. A {@link Label} declares one target: a game
 * {@link Version}, a runtime distribution, and a physical {@link Environment}.
 * {@link Compatibility} is the ternary outcome of matching content against a
 * target, and {@link Parity} records which multiplayer sides content runs on.
 *
 * <p>
 * Versions are modelled after discovery; finding and loading them belongs to
 * providers.
 */
package cat.tabbie.sdk.minecraft;
