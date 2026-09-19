package cat.tabbie.sdk.minecraft;

/**
 * <h2>Environment</h2>
 *
 * Physical runtime being managed. A client may host an integrated logical
 * server; that does not turn it into a dedicated server. World bindings and
 * resource-pack delivery are separate installation concerns.
 */
public enum Environment {

	/**
	 * Dedicated multiplayer server process
	 */
	SERVER,

	/**
	 * Integrated singleplayer server process.
	 */
	CLIENT
}
