package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Compatibility;
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
   * Declares support for the entire artifact.
   *
   * @return immutable, nonempty supported targets
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
	 * Assesses type support followed by explicit target declarations. Unlisted
	 * targets remain unknown; no compatibility between runtime forks is inferred.
	 * 
	 * @param target target to assess
	 * @return structural rejection, declared support, or unknown support
	 */
	default Compatibility compatibility(@NonNull Label target) {
		if (!target.distribution().capabilities().contains(getClass())) {
			return Compatibility.UNSUPPORTED;
		}
		return labels().stream().anyMatch(label -> label.match(target))
				? Compatibility.SUPPORTED
				: Compatibility.UNKNOWN;
	}
}
