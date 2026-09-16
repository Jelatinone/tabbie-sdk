package cat.tabbie.sdk.minecraft;

/**
 *
 * <h1>Environment</h1>
 *
 * <p>
 * Physical runtime being managed. A client may host an integrated logical
 * server; that does not turn it into a dedicated server. World bindings and
 * resource-pack delivery are separate installation concerns.
 * </p>
 */
public enum Environment {

  SERVER,

  CLIENT
}
