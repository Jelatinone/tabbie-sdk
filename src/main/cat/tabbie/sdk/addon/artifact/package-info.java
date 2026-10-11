/**
 * Addon content and its logical placement. {@link Artifact} is sealed over one
 * family per kind of content: {@link Mod}, {@link Plugin}, {@link Datapack},
 * {@link Resourcepack} (Java and Bedrock), {@link Behaviourpack}, and
 * {@link Modpack}. Each family has a {@code Default} variant, placed by the
 * target distribution's layout, and a {@code Custom} variant that supplies its
 * own installer.
 *
 * <p>
 * Installing an artifact captures its bytes into the caller's retention
 * backend and describes the resulting images; it never writes to the target
 * installation filesystem. Relations to other content name catalog
 * coordinates at any level.
 */
package cat.tabbie.sdk.addon.artifact;
