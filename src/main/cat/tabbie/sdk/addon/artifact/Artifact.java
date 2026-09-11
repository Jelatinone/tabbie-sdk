package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.Image;
import lombok.NonNull;

public sealed interface Artifact permits Mod {

	/**
	 * 
	 * @return
	 */
	@NonNull
	Identity<Artifact> artifactId();

	/**
	 * 
	 * @return
	 */
	@NonNull
	String artifactName();

	/**
	 * 
	 * @return
	 */
	long artifactSize();

	/**
	 * 
	 * @return
	 */
	@NonNull
	Set<Identity<Artifact>> artifactDependsOn();

	/**
	 * 
	 * @return
	 */
	@NonNull
	Set<Identity<Artifact>> artifactConflictsOn();

	/**
	 * 
	 * @return
	 */
	@NonNull
	Set<Image<?>> artifactImages();

}
