package cat.tabbie.sdk.minecraft;

/**
 * Which multiplayer sides content runs on and what it requires of the other
 * side. This is separate from install compatibility (whether the bytes run on
 * a target). A client may host an integrated server, so server-side content
 * can still be installed on a client for singleplayer.
 */
public enum Parity {

  /**
   * Runs only on the server; clients need nothing.
   */
  ONLY_SERVER,

  /**
   * Runs on the server; connecting clients must install it too.
   */
  SERVER_AND_REQUIRED_CLIENT,

  /**
   * Runs on the server; clients may install it for extra behavior.
   */
  SERVER_OR_OPTIONALLY_CLIENT,

  /**
   * Runs only on clients; servers need nothing.
   */
  ONLY_CLIENT,

  /**
   * No side evidence is available.
   */
  UNKNOWN,
}
