package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 * 
 * <h1>Artifact</h1>
 * 
 * <p>
 * Described artifact content backed by a supplied store sink, without access to
 * the target filesystem.
 * </p>
 * 
 */
public interface Artifact {

	/**
	 * Stable artifact identity
	 *
	 * @return artifact identity
	 */
	@NonNull
	Identity<Artifact> artifactId();

	/**
	 * Human-readable canonical artifact name
	 * 
	 * @return artifact name
	 */
	@NonNull
	String artifactName();

	/**
	 * Declared own payload size as bytes, excluding dependencies or other stored
	 * content beyond what would produced by related {@link Image.File file}
	 * operations.
	 * 
	 * @return payload size
	 */
	long artifactSize();

	/**
	 * Delegated storage receiving capture/open calls
	 * 
	 * @return delegated storage
	 */
	Store store();

	/**
	 * 
	 * @return
	 */
	@NonNull
	Set<Label> labels();

	/**
	 * Artifact identities that must be installed alongside this artifact
	 * 
	 * @return depending artifact identities
	 */
	@NonNull
	Set<Identity<Artifact>> depends();

	/**
	 * Artifact identities that may not be installed alongside this artifact
	 * 
	 * @return conflicting artifact identities
	 */
	@NonNull
	Set<Identity<Artifact>> conflicts();

	/**
	 * Payload installation images relative to this type's installation root.
	 * 
	 * @return installation images
	 */
	@NonNull
	Set<Image<?>> images();

	/**
	 * Compatibility state of this artifact against a label's explicit target
	 * declarations.
	 * 
	 * @param target target label
	 * @return Compatibility state
	 */
	@NonNull
	boolean allow(@NonNull Label target);
}
