/**
 * Content bytes and where they come from. {@link Describe} is the source role,
 * a reusable description of how to obtain bytes; a {@link Reference} is the
 * identity of bytes, expected or verified. A {@link Stash} is the store of
 * captured content: it captures described bytes and reopens them by
 * reference, and every {@link cat.tabbie.sdk.album.model.Plate Plate} is one. A
 * {@link Store} is a source with a lifetime. {@link Archive} bounds and unpacks
 * ZIP content into the plate's stash.
 *
 * <p>
 * Describing performs no I/O. Captured bytes are verified by digest and size
 * before they are published, and sources stay caller-owned.
 */
package cat.tabbie.sdk.album.repository;
