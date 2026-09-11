package cat.tabbie.sdk.minecraft;

/**
 * 
 * <h1>Version</h1>
 * 
 * <p>
 * A specific, self-contained snapshot of the game's source code and asset
 * libraries compiled into a runnable package. It defines the exact rules,
 * mechanics, items, and structures that exist within the game world at the
 * moment it is launched.
 * </p>
 */
public sealed interface Version {

	public enum Java implements Version {
		// TODO: All Java Versions
	}

	public enum Bedrock implements Version {
		// TODO: All Bedrock Versions
	}
}
