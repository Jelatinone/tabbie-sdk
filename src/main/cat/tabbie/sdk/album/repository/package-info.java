/**
 * Where content bytes are kept and come from. A {@link Stash} is the store of
 * captured content: it captures bytes a
 * {@link cat.tabbie.sdk.platform.Describe Describe} names and reopens them by
 * {@link cat.tabbie.sdk.platform.Reference Reference}, and every
 * {@link cat.tabbie.sdk.album.model.Plate Plate} is one. A {@link Store} is a
 * source with a lifetime. {@link Archive} bounds and unpacks ZIP content into
 * the plate's stash.
 *
 * <p>
 * Describing performs no I/O. Captured bytes are verified by digest and size
 * before they are published, and sources stay caller-owned.
 */
package cat.tabbie.sdk.album.repository;
