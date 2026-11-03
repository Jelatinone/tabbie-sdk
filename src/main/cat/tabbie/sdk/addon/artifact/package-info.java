/**
 * Addon content and its logical placement. An {@link Artifact} is one piece
 * of content of a family: {@link Mod}, {@link Plugin}, {@link Datapack},
 * {@link Resourcepack} (Java and Bedrock), {@link Behaviourpack}, and
 * {@link Modpack}. Each family is
 * {@link cat.tabbie.sdk.meta.registry.Registered registered}, and has a
 * {@code Default} variant placed beneath the directory of the model that
 * accepts its family. {@code Artifact} is open, so a module can register
 * families of its own.
 *
 * <p>
 * Installing an artifact captures its bytes into the stash of the plate its
 * context names and describes the resulting images; it never writes to the
 * Cat's files. Relations to other content name catalog coordinates at any
 * level.
 */
package cat.tabbie.sdk.addon.artifact;
