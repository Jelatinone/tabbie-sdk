/**
 * The context work runs in. A {@link Plate} carries every effect of an
 * {@link cat.tabbie.sdk.album.revision.Intermediate Intermediate} or
 * {@link cat.tabbie.sdk.album.revision.Image Image}: it leases the files
 * beneath its root, captures content into the stash, and supplies scratch
 * space and an observer. A {@link cat.tabbie.sdk.platform.layout.Lease Lease}
 * holds one file for reading, writing, or both until it closes, and a
 * {@link cat.tabbie.sdk.platform.layout.Refusal Refusal} says why a plate would
 * not carry out an operation.
 *
 * <p>
 * The SDK defines plates and leases but implements neither: whoever runs the
 * work supplies the plate, and so decides whether it rehearses over an overlay
 * or acts on the files beneath its root.
 */
package cat.tabbie.sdk.album.model;
