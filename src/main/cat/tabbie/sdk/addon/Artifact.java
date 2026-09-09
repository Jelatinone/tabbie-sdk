package cat.tabbie.sdk.addon;

import java.net.URI;

public record Artifact(
		ArtifactIdentity artifactId,
		long artifactSize,

		AddonIdentity addonOf) {
}
