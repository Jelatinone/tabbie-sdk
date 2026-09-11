package cat.tabbie.sdk.addon;

import cat.tabbie.sdk.Identity;

public record Mod(
		Identity<Artifact> artifactId

) implements Artifact {
	// TODO: Needs work
}
