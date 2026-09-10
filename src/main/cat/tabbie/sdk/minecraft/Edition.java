package cat.tabbie.sdk.minecraft;

/**
 * 
 * <h1>Edition</h1>
 * 
 * <p>
 * A minecraft edition kind which supports devices like PC, consoles,
 * mobile devices, or specialized environments
 * <p/>
 */
public enum Edition {
	// TODO: Need to decide the exact contracts that will exist so that the
	// agent-layer can interop with a single interface without worrying about the
	// underlying edition. Where does this system live exactly? This has
	// long-lasting impacts, as bedrock and java servers are fundamentally very
	// incompatible and have different file setups and architectures. This enum
	// works great for describing what something supports, i.e. an addon but not
	// great for agent-layer logic. Needs revision or extensions which implement
	// either edition int he future.

	JAVA_EDITION,
	BEDROCK_EDITION
}
