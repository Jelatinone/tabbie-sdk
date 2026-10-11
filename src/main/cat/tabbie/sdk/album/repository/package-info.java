/**
 * Content bytes and where they come from. {@link Describe} is the source role,
 * a reusable description of how to obtain bytes; {@link Extract} is the
 * retention role, which captures described bytes and reopens them by
 * {@link Reference}. A {@link Store} combines both roles. {@link Archive}
 * bounds and unpacks ZIP content into retention.
 *
 * <p>
 * Describing performs no I/O. Captured bytes are verified by digest and size
 * before they are published, and sources and retention backends stay
 * caller-owned.
 */
package cat.tabbie.sdk.album.repository;
