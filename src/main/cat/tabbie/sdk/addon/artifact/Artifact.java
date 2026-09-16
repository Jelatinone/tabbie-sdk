package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Compatibility;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

public interface Artifact extends Store {

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
	Compatibility allow(@NonNull Label target);
}
