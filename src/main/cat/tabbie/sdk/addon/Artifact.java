package cat.tabbie.sdk.addon;

import cat.tabbie.sdk.Identity;

public sealed interface Artifact permits Mod {

	Identity<Artifact> artifactId();

	// TODO: Needs work, what belongs here?

}
