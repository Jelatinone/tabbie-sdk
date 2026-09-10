package cat.tabbie.sdk.addon;

import cat.tabbie.sdk.Identity.AddonIdentity;
import cat.tabbie.sdk.Identity.ArtifactIdentity;

public record Artifact(
		ArtifactIdentity artifactId,
		long artifactSize,

		AddonIdentity addonOf) {
}
