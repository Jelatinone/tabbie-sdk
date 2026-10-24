/**
 * The context work runs in. A {@link Plate} carries every effect of an
 * {@link cat.tabbie.sdk.album.revision.Intermediate Intermediate} or
 * {@link cat.tabbie.sdk.album.revision.Image Image}: it leases a Cat's files,
 * captures content into the stash, and supplies scratch space and an
 * observer. A {@link Lease} holds one file for reading, writing, or both until
 * it closes, and a {@link Refusal} says why a plate would not carry out an
 * operation.
 *
 * <p>
 * The SDK defines plates and leases but implements neither: whoever runs the
 * work supplies the plate, and so decides whether it rehearses over an overlay
 * or acts on the Cat itself.
 */
package cat.tabbie.sdk.album.model;
